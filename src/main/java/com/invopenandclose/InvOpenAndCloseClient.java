package com.invopenandclose;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InvOpenAndCloseClient implements ClientModInitializer {
    private static final int DELAY_TICKS = 2;
    private static final int OPEN_TICKS = 2;

    private int delayTicks;
    private int openTicks;
    private boolean watching = true;
    private boolean inventoryFixActive;
    private int lastSlot = -1;
    private int lastAnchorCount = 0;

    @Override
    public void onInitializeClient() {
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

        if (watching && lastAnchorCount != 1 && anchorCount == 1) {
            delayTicks = DELAY_TICKS;
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
            return;
        }

        if (--openTicks <= 0) {
            client.gui.setScreen(null);
            inventoryFixActive = false;
        }
    }

    private void resetTracking() {
        delayTicks = 0;
        openTicks = 0;
        inventoryFixActive = false;
        lastSlot = -1;
        lastAnchorCount = 0;
    }

    private static boolean isAnchor(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.RESPAWN_ANCHOR);
    }
}
