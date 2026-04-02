package com.yuyinrl.resourceobserver.world.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ObserverCommands {
    private ObserverCommands() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ObserverCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("observer")
                        .then(Commands.literal("debug")
                                .executes(ctx -> executeDebugToggle(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> executeDebugSet(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> executeDebugSet(ctx.getSource(), false)))
                                .then(Commands.literal("status")
                                        .executes(ctx -> executeDebugStatus(ctx.getSource()))))
        );
    }

    private static int executeDebugToggle(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        PlayerUiPrefsSavedData data = PlayerUiPrefsSavedData.get(player.serverLevel());
        boolean enabled = data.toggleObserverDebugEnabled(player.getUUID());
        source.sendSuccess(
                () -> Component.translatable(enabled
                        ? "message.resourceobserver.command.debug.enabled"
                        : "message.resourceobserver.command.debug.disabled"),
                false
        );
        return 1;
    }

    private static int executeDebugSet(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        PlayerUiPrefsSavedData data = PlayerUiPrefsSavedData.get(player.serverLevel());
        data.setObserverDebugEnabled(player.getUUID(), enabled);
        source.sendSuccess(
                () -> Component.translatable(enabled
                        ? "message.resourceobserver.command.debug.enabled"
                        : "message.resourceobserver.command.debug.disabled"),
                false
        );
        return 1;
    }

    private static int executeDebugStatus(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        PlayerUiPrefsSavedData data = PlayerUiPrefsSavedData.get(player.serverLevel());
        boolean enabled = data.isObserverDebugEnabled(player.getUUID());
        source.sendSuccess(
                () -> Component.translatable(enabled
                        ? "message.resourceobserver.command.debug.status_on"
                        : "message.resourceobserver.command.debug.status_off"),
                false
        );
        return 1;
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException ex) {
            source.sendFailure(Component.translatable("message.resourceobserver.command.only_player"));
            return null;
        }
    }
}
