package com.invopenandclose;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InvOpenAndCloseClient implements ClientModInitializer {
    private static final int OPEN_DELAY_TICKS = 10;
    private static final int OPEN_TICKS = 40;
    private static final int SELL_DELAY_TICKS = 10;

    private int delayTicks;
    private int openTicks;
    private int sellDelayTicks;
    private boolean inventoryFixActive;
    private boolean sellEnabled;
    private int sellAmount;
    private int lastSlot = -1;
    private int lastAnchorCount = 0;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(ClientCommands.literal("ksell")
                .then(ClientCommands.literal("on")
                    .then(ClientCommands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .executes(context -> {
                            sellEnabled = true;
                            sellAmount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "amount");
                            context.getSource().sendFeedback(Component.literal("KSell: ON | Amount: " + sellAmount));
                            return 1;
                        })))
                .then(ClientCommands.literal("off")
                    .executes(context -> {
                        sellEnabled = false;
                        sellDelayTicks = 0;
                        context.getSource().sendFeedback(Component.literal("KSell: OFF"));
                        return 1;
                    }))
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        LocalPlayer player = client.player;

        if (player == null || client.level == null) {
            resetTracking();
            return;
        }

        int selectedSlot = player.getInventory().getSelectedSlot();
        ItemStack selected = player.getInventory().getItem(selectedSlot);
        int anchorCount = isAnchor(selected) ? selected.getCount() : 0;

        if (lastSlot != selectedSlot) {
            lastSlot = selectedSlot;
            lastAnchorCount = anchorCount;
        }

        if (inventoryFixActive) {
            handleInventoryFix(client, player);
            return;
        }

        if (sellDelayTicks > 0) {
            if (client.gui.screen() != null) {
                return;
            }

            if (--sellDelayTicks <= 0 && sellEnabled) {
                client.player.connection.sendCommand("ah sell " + sellAmount);
            }
            lastAnchorCount = anchorCount;
            return;
        }

        if (delayTicks > 0) {
            if (client.gui.screen() != null) {
                return;
            }

            if (--delayTicks <= 0) {
                client.gui.setScreen(new InventoryScreen(player));
                openTicks = OPEN_TICKS;
                inventoryFixActive = true;
            }

            lastAnchorCount = anchorCount;
            return;
        }

        if (client.gui.screen() != null) {
            return;
        }

        if (lastAnchorCount != 1 && anchorCount == 1) {
            delayTicks = OPEN_DELAY_TICKS;
        }

        lastAnchorCount = anchorCount;
    }

    private void handleInventoryFix(Minecraft client, LocalPlayer player) {
        if (client.gui.screen() == null) {
            client.gui.setScreen(new InventoryScreen(player));
            return;
        }

        if (!(client.gui.screen() instanceof InventoryScreen)) {
            client.gui.setScreen(null);
            inventoryFixActive = false;
            sellDelayTicks = SELL_DELAY_TICKS;
            return;
        }

        if (--openTicks <= 0) {
            client.gui.setScreen(null);
            inventoryFixActive = false;
            sellDelayTicks = SELL_DELAY_TICKS;
        }
    }

    private void resetTracking() {
        delayTicks = 0;
        openTicks = 0;
        sellDelayTicks = 0;
        inventoryFixActive = false;
        lastSlot = -1;
        lastAnchorCount = 0;
    }

    private static boolean isAnchor(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.RESPAWN_ANCHOR);
    }
}
