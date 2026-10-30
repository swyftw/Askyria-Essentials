package com.askyria.essentials;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class GodMaceManager implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    public GodMaceManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private ItemStack createGodMace() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        meta.displayName(Component.text("⚡ GOD MACE ⚡"));
        meta.setItemModel(NamespacedKey.fromString("askyria:god_mace"));
        meta.lore(List.of(
            Component.text("A legendary weapon of Askyria"),
            Component.text("Smash with the force of a storm.")
        ));
        meta.addEnchant(Enchantment.WIND_BURST, 2, true);
        meta.addEnchant(Enchantment.BREACH, 4, true);
        meta.addEnchant(Enchantment.DENSITY, 6, true);
        meta.addEnchant(Enchantment.UNBREAKING, 5, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        mace.setItemMeta(meta);
        return mace;
    }

    private boolean isGodMace(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && Component.text("⚡ GOD MACE ⚡").equals(meta.displayName());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("Only operators can use /godmace."));
            return true;
        }
        Player target;
        if (args.length == 0 || args[0].equalsIgnoreCase("@s")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console usage: /godmace <player>");
                return true;
            }
            target = player;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("That player must be online."));
                return true;
            }
        }
        target.getInventory().addItem(createGodMace()).values().forEach(item ->
            target.getWorld().dropItemNaturally(target.getLocation(), item)
        );
        sender.sendMessage(Component.text("Gave " + target.getName() + " the God Mace."));
        return true;
    }

    @EventHandler
    public void onGodMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!isGodMace(player.getInventory().getItemInMainHand())) return;
        if (player.getFallDistance() > 1.5F) {
            event.setDamage(event.getDamage() + Math.min(12.0, player.getFallDistance() * 1.5));
        }
        target.getWorld().strikeLightningEffect(target.getLocation());
    }
}
