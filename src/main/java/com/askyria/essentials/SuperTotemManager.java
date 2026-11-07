package com.askyria.essentials;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

public final class SuperTotemManager implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final NamespacedKey superTotemKey;

    public SuperTotemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.superTotemKey = new NamespacedKey(plugin, "super_totem");
    }

    private ItemStack createSuperTotem() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("SUPER TOTEM"));
        meta.lore(List.of(
            Component.text("A powerful relic of Askyria."),
            Component.text("On activation: thunder and repel nearby enemies."),
            Component.text("Strength II, Regeneration II, Speed II"),
            Component.text("Resistance I, Fire Resistance I, Absorption IV"),
            Component.text("All effects last 90 seconds.")
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.getPersistentDataContainer().set(superTotemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isSuperTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING
            && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(superTotemKey, PersistentDataType.BYTE);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("Only operators can use /supertotem."));
            return true;
        }

        Player target;
        if (args.length == 0 || args[0].equalsIgnoreCase("@s")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console usage: /supertotem <player>"));
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

        target.getInventory().addItem(createSuperTotem()).values().forEach(item ->
            target.getWorld().dropItemNaturally(target.getLocation(), item)
        );
        sender.sendMessage(Component.text("Gave " + target.getName() + " a SUPER TOTEM."));
        if (!target.equals(sender)) {
            target.sendMessage(Component.text("You received a SUPER TOTEM."));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSuperTotemActivate(EntityResurrectEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!isSuperTotem(mainHand) && !isSuperTotem(offHand)) return;

        player.getWorld().strikeLightningEffect(player.getLocation());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.5f, 0.7f);

        // Restore the original 90-second Super Totem buffs.
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 90 * 20, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 90 * 20, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 90 * 20, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 90 * 20, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 90 * 20, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 90 * 20, 3));

        // Keep the enemy knockback, but do not summon any zombies.
        for (Entity nearby : player.getNearbyEntities(8.0, 5.0, 8.0)) {
            if (!(nearby instanceof LivingEntity enemy) || enemy.equals(player)) continue;
            if (enemy instanceof Player && !player.getWorld().getPVP()) continue;
            Vector push = enemy.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() < 0.01) push = player.getLocation().getDirection().clone();
            push.normalize().multiply(2.2).setY(0.65);
            enemy.setVelocity(push);
        }

        player.sendMessage(Component.text("SUPER TOTEM ACTIVATED! All six effects last 1:30."));
        showEffectCountdown(player, 90);
    }

    private void showEffectCountdown(Player player, int secondsLeft) {
        if (!player.isOnline() || secondsLeft <= 0) return;

        String message = "Strength II | Regeneration II | Speed II | Resistance I | Fire Resistance I | Absorption IV | "
            + secondsLeft + "s";
        player.sendActionBar(Component.text(message));

        Bukkit.getScheduler().runTaskLater(plugin, () -> showEffectCountdown(player, secondsLeft - 1), 20L);
    }
}
