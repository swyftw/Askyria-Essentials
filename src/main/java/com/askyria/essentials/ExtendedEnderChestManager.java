package com.askyria.essentials;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;

/**
 * Replaces the normal 27-slot Ender Chest with a persistent 45-slot menu.
 * Existing vanilla Ender Chest contents are migrated on a player's first open.
 */
public final class ExtendedEnderChestManager implements Listener {
    private static final int SIZE = 45;
    private static final String TITLE = "Ender Chest";

    private final AskyriaEssentials plugin;
    private final Map<UUID, Inventory> inventories = new HashMap<>();

    public ExtendedEnderChestManager(AskyriaEssentials plugin) {
        this.plugin = plugin;
    }

    public Inventory getInventory(Player owner) {
        UUID id = owner.getUniqueId();
        Inventory existing = inventories.get(id);
        if (existing != null) return existing;

        ExtendedHolder holder = new ExtendedHolder(id);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text(TITLE));
        holder.inventory = inventory;

        String path = storagePath(id);
        if (plugin.getConfig().isConfigurationSection(path)) {
            var section = plugin.getConfig().getConfigurationSection(path);
            for (String key : section.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < SIZE) {
                        ItemStack item = section.getItemStack(key);
                        if (item != null && !item.getType().isAir()) inventory.setItem(slot, item);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed slot keys rather than risking player items.
                }
            }
        } else {
            // First open: safely migrate the player's existing 27 vanilla slots.
            ItemStack[] oldContents = owner.getEnderChest().getContents();
            for (int slot = 0; slot < Math.min(oldContents.length, SIZE); slot++) {
                ItemStack item = oldContents[slot];
                if (item != null && !item.getType().isAir()) inventory.setItem(slot, item.clone());
            }
            owner.getEnderChest().clear();
            saveInventory(inventory);
        }

        inventories.put(id, inventory);
        return inventory;
    }

    @EventHandler
    public void onEnderChestInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        player.openInventory(getInventory(player));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ExtendedHolder)) return;
        saveInventory(event.getInventory());
    }

    public void saveAll() {
        for (Inventory inventory : inventories.values()) saveInventory(inventory);
    }

    private void saveInventory(Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof ExtendedHolder holder)) return;
        String path = storagePath(holder.ownerId);
        plugin.getConfig().set(path, null);
        var section = plugin.getConfig().createSection(path);
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) section.set(Integer.toString(slot), item.clone());
        }
        plugin.saveConfig();
    }

    private String storagePath(UUID id) {
        return "extended-ender-chests." + id;
    }

    private static final class ExtendedHolder implements InventoryHolder {
        private final UUID ownerId;
        private Inventory inventory;

        private ExtendedHolder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
