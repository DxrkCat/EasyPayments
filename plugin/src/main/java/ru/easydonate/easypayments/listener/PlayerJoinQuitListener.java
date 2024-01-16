package ru.easydonate.easypayments.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import ru.easydonate.easydonate4j.exception.HttpRequestException;
import ru.easydonate.easydonate4j.exception.HttpResponseException;
import ru.easydonate.easypayments.EasyPaymentsPlugin;
import ru.easydonate.easypayments.command.exception.ExecutionException;
import ru.easydonate.easypayments.config.Messages;
import ru.easydonate.easypayments.database.DatabaseManager;
import ru.easydonate.easypayments.database.model.Customer;
import ru.easydonate.easypayments.database.model.Payment;
import ru.easydonate.easypayments.database.model.Purchase;
import ru.easydonate.easypayments.shopcart.ShopCart;
import ru.easydonate.easypayments.shopcart.ShopCartStorage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class PlayerJoinQuitListener implements Listener {

    private final EasyPaymentsPlugin plugin;
    private final BukkitScheduler scheduler;

    private final Messages messages;
    private final ShopCartStorage shopCartStorage;

    public PlayerJoinQuitListener(
            @NotNull EasyPaymentsPlugin plugin,
            @NotNull Messages messages,
            @NotNull ShopCartStorage shopCartStorage
    ) {
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();

        this.messages = messages;
        this.shopCartStorage = shopCartStorage;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        if(!EasyPaymentsPlugin.isPluginEnabled())
            return;

        Player player = event.getPlayer();
        scheduler.runTaskAsynchronously(plugin, () -> {
            updateCustomerOwnership(player);
            notifyAboutVersionUpdate(player);
            shopCartStorage.loadAndCache(player)
                    .thenAccept(shopCart -> notifyAboutCartContent(player, shopCart))
                    .join();
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        if(!EasyPaymentsPlugin.isPluginEnabled())
            return;

        Player player = event.getPlayer();
        shopCartStorage.unloadCached(player.getName());
    }

    private void notifyAboutCartContent(@NotNull Player player, @NotNull ShopCart shopCart) {
        if(!player.hasPermission("easypayments.notify.cart"))
            return;

        if(shopCart == null || !shopCart.hasContent()) {
            return;
        }

        getShopCart(player, shopCart);
        messages.getAndSend(player, "cart-notification");
    }

    public void getShopCart(@NotNull Player player, @NotNull ShopCart shopCart) {
        Collection<Payment> cartContent = shopCart.getContent();

        if(cartContent.isEmpty()) {
            try {
                throw new ExecutionException(messages.get("cart-get.failed.no-purchases"));
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        uploadReports(cartContent).thenRun(() -> {
            List<String> purchases = cartContent.stream()
                    .filter(Payment::hasPurchases)
                    .map(Payment::getPurchases)
                    .flatMap(Collection::stream)
                    .map(this::asBodyElement)
                    .collect(Collectors.toList());

            List<String> message = new ArrayList<>();
            message.add(" ");
            message.addAll(purchases);
            message.add(" ");
            message.removeIf(String::isEmpty);

            messages.send(player, String.join("\n", message));
        });
    }

    private void notifyAboutVersionUpdate(@NotNull Player player) {

        if(!player.hasPermission("easypayments.notify.update"))
            return;

        plugin.getVersionResponse().ifPresent(response -> messages.getAndSend(player, "update-notification",
                "%current_version%", plugin.getDescription().getVersion(),
                "%available_version%", response.getVersion(),
                "%download_url%", response.getDownloadUrl()
        ));
    }
    private @NotNull String asBodyElement(@NotNull Purchase purchase) {
        String name = purchase.getName();
        int amount = purchase.getAmount();
        LocalDateTime createdAt = purchase.getCreatedAt();

        return messages.get("cart-get.body",
                "%name%", name != null ? name : getNoValueStub(),
                "%amount%", Math.max(amount, 1),
                "%time_ago%", plugin.getRelativeTimeFormatter().formatElapsedTime(createdAt)
        );
    }
    private @NotNull String getNoValueStub() {
        return messages.get("cart-get.no-value-stub");
    }

    private @NotNull CompletableFuture<Void> uploadReports(@NotNull Collection<Payment> payments) {
        return CompletableFuture.runAsync(() -> {
            try {
                plugin.getExecutionController().givePurchasesFromCartAndReport(payments);
            } catch (HttpRequestException | HttpResponseException ex) {
                plugin.getLogger().severe("An unknown error occured while trying to upload reports!");
                plugin.getLogger().severe("Please, contact with the platform support:");
                plugin.getLogger().severe("https://vk.me/easydonateru");
                ex.printStackTrace();
            }
        });
    }

    private void updateCustomerOwnership(@NotNull Player player) {
        DatabaseManager databaseManager = shopCartStorage.getStorage();

        Customer customer = databaseManager.getCustomer(player).join();
        if(customer == null)
            return;

        if(databaseManager.isUuidIdentificationEnabled()) {
            // UUID = constant, updating player name
            if(!player.getName().equals(customer.getPlayerName())) {
                databaseManager.transferCustomerOwnership(customer, player.getName()).join();
            }
        } else {
            // name = constant, updating player UUID
            if(!player.getUniqueId().equals(customer.getPlayerUUID())) {
                customer.updateUUID(player.getUniqueId());
                databaseManager.saveCustomer(customer).join();
            }
        }
    }

}
