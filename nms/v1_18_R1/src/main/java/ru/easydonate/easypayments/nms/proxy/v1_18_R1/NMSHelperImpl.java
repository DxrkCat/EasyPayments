package ru.easydonate.easypayments.nms.proxy.v1_18_R1;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.ICommandListener;
import net.minecraft.network.chat.ChatComponentText;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.phys.Vec2F;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_18_R1.CraftServer;
import org.bukkit.craftbukkit.v1_18_R1.command.ProxiedNativeCommandSender;
import org.bukkit.craftbukkit.v1_18_R1.command.ServerCommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import ru.easydonate.easypayments.execution.FeedbackInterceptor;
import ru.easydonate.easypayments.gui.item.wrapper.NotchianItemWrapper;
import ru.easydonate.easypayments.nms.NMSHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@SuppressWarnings({"ConstantConditions", "ClassCanBeRecord"})
public final class NMSHelperImpl implements NMSHelper {

    private final String username;
    private final int permissionLevel;

    @Override
    public @NotNull FeedbackInterceptor createFeedbackInterceptor() {
        MinecraftServer minecraftServer = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer worldServer = minecraftServer.a(WorldServer.f);

        InterceptedCommandListener commandListener = new InterceptedCommandListener(username);
        InterceptedCommandListenerWrapper listenerWrapper = new InterceptedCommandListenerWrapper(
                commandListener,
                worldServer,
                username,
                permissionLevel
        );

        return new InterceptedProxiedSender(listenerWrapper, commandListener);
    }

    @Override
    public @NotNull NotchianItemWrapper createNotchianItemWrapper(@NotNull ItemStack bukkitItem) {
        return new NMSItemWrapper(bukkitItem);
    }

    @Getter
    private static final class InterceptedCommandListenerWrapper extends CommandListenerWrapper implements FeedbackInterceptor {
        private static final Vec3D POSITION = new Vec3D(0D, 0D, 0D);
        private static final Vec2F DIRECTION = new Vec2F(0F, 0F);

        private final InterceptedCommandListener icommandlistener;

        public InterceptedCommandListenerWrapper(InterceptedCommandListener icommandlistener, WorldServer worldserver, String username, int permissionLevel) {
            super(icommandlistener, POSITION, DIRECTION, worldserver, permissionLevel, username, new ChatComponentText(username), worldserver.n(), null);
            this.icommandlistener = icommandlistener;
        }

        @Override
        public List<String> getFeedbackMessages() {
            return icommandlistener.getFeedbackMessages();
        }
    }

    @Getter
    private static final class InterceptedCommandListener extends ServerCommandSender implements ICommandListener {
        private final String username;
        private final List<String> feedbackMessages;

        public InterceptedCommandListener(String username) {
            this.username = username;
            this.feedbackMessages = new ArrayList<>();
        }

        @Override
        public void a(IChatBaseComponent iChatBaseComponent, UUID uuid) {
            feedbackMessages.add(iChatBaseComponent.getString());
        }

        @Override
        public boolean i_() {
            return true;
        }

        @Override
        public boolean j_() {
            return true;
        }

        @Override
        public boolean F_() {
            return true;
        }

        @Override
        public CommandSender getBukkitSender(CommandListenerWrapper commandListenerWrapper) {
            return this;
        }

        @Override
        public void sendMessage(String message) {
            feedbackMessages.add(message);
        }

        @Override
        public void sendMessage(String[] messages) {
            feedbackMessages.addAll(Arrays.asList(messages));
        }

        @Override
        public String getName() {
            return username;
        }

        @Override public boolean isOp() { return true; }
        @Override public void setOp(boolean value) {}

        @Override public boolean hasPermission(String name) { return true; }
        @Override public boolean hasPermission(Permission perm) { return true; }

        @Override public boolean isPermissionSet(String name) { return true; }
        @Override public boolean isPermissionSet(Permission perm) { return true; }
    }

    private static final class InterceptedProxiedSender extends ProxiedNativeCommandSender implements FeedbackInterceptor {
        public InterceptedProxiedSender(InterceptedCommandListenerWrapper orig, CommandSender sender) {
            super(orig, sender, sender);
        }

        @Override
        public InterceptedCommandListenerWrapper getHandle() {
            return (InterceptedCommandListenerWrapper) super.getHandle();
        }

        @Override
        public List<String> getFeedbackMessages() {
            return getHandle().getFeedbackMessages();
        }
    }

}
