package com.askyria.essentials;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Safely returns legacy 45-slot custom Ender Chests to vanilla 27-slot chests. */
public final class LegacyEnderChestMigration implements Listener {
    private final JavaPlugin plugin;

    public LegacyEnderChestMigration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String path = "extended-ender-chests." + player.getUniqueId();
        if (!plugin.getConfig().isConfigurationSection(path)) return;
        Bukkit.getScheduler().runTask(plugin, () -> migrate(player, path));
    }

    private void migrate(Player player, String path) {
        var section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return;
        List<ItemStack> overflow = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack item = section.getItemStack(key);
                if (item == null || item.getType().isAir()) continue;
                if (slot >= 0 && slot < 27) {
                    player.getEnderChest().setItem(slot, item.clone());
                } else {
                    overflow.add(item.clone());
                }
            } catch (NumberFormatException ignored) { }
        }
        for (ItemStack item : overflow) {
            player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        plugin.getConfig().set(path, null);
        plugin.saveConfig();
        player.sendMessage("Your Ender Chest is back to the normal 27 slots. Extra-row items were moved to your inventory or dropped nearby if it was full.");
    }
}