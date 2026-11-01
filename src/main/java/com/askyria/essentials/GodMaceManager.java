package com.askyria.essentials;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class GodMaceManager implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final Map<UUID, UUID> lastMeleeAttacker = new ConcurrentHashMap<>();

    public GodMaceManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private ItemStack createGodMace() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        meta.displayName(Component.text("☠ JUDGEMENT ☠"));
        meta.setItemModel(NamespacedKey.fromString("askyria:god_mace"));
        meta.setEnchantmentGlintOverride(false);
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
        return meta.hasDisplayName() && Component.text("☠ JUDGEMENT ☠").equals(meta.displayName());
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
    public void trackWardenMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Warden warden)) return;
        if (!(event.getDamager() instanceof Player player)) return;
        lastMeleeAttacker.put(warden.getUniqueId(), player.getUniqueId());
    }

    @EventHandler
    public void onWardenDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Warden warden)) return;
        UUID lastAttacker = lastMeleeAttacker.remove(warden.getUniqueId());
        Player killer = warden.getKiller();
        if (killer == null || lastAttacker == null || !killer.getUniqueId().equals(lastAttacker)) return;
        if (killer.getLocation().distanceSquared(warden.getLocation()) > 64.0) return;
        // 0.1% chance (1 in 1,000), only for a close-range player melee kill.
        if (ThreadLocalRandom.current().nextDouble() >= 0.001) return;
        event.getDrops().add(createGodMace());
        killer.sendMessage(Component.text("§5The Warden dropped the legendary JUDGEMENT God Mace!"));
        killer.getWorld().playSound(killer.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 0.7f);
    }

    @EventHandler
    public void onGodMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!isGodMace(player.getInventory().getItemInMainHand())) return;

        if (player.getFallDistance() > 1.5F) {
            event.setDamage(event.getDamage() + Math.min(12.0, player.getFallDistance() * 1.5));
        }

        // Visual-only lightning: dramatic bolt and thunder audio without extra lightning damage or fire.
        target.getWorld().strikeLightningEffect(target.getLocation());
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0F, 1.0F);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5F, 1.0F);
        target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1);
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 35, 0.45, 0.7, 0.45, 0.15);
    }
}
