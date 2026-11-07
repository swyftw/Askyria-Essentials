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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public final class SuperTotemManager implements Listener, CommandExecutor {
    private static final int EFFECT_TICKS = 90 * 20;

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
            Component.text("On activation: thunder and powerful effects."),
            Component.text("Strength II • Regeneration II • Speed II"),
            Component.text("Resistance I • Fire Resistance • Absorption IV"),
            Component.text("Effects last 1 minute 30 seconds.")
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
    public void onSuperTotemActivate(PlayerResurrectEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack usedItem = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();

        if (!isSuperTotem(usedItem)) return;

        player.getWorld().strikeLightningEffect(player.getLocation());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.5f, 0.7f);

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, EFFECT_TICKS, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, EFFECT_TICKS, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, EFFECT_TICKS, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, EFFECT_TICKS, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, EFFECT_TICKS, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, EFFECT_TICKS, 3));

        player.sendMessage(Component.text("SUPER TOTEM ACTIVATED! Thunder has awakened your power for 1:30."));
    }
}
