package com.askyria.essentials;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GodSpearManager implements Listener, CommandExecutor {
    private static final long COOLDOWN_MS = 20_000L;
    private static final double IMPACT_RADIUS = 4.5;
    private static final double IMPACT_DAMAGE = 8.0;
    private final JavaPlugin plugin;
    private final NamespacedKey spearKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BossBar> cooldownBars = new HashMap<>();
    private BukkitTask cooldownTicker;

    public GodSpearManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.spearKey = new NamespacedKey(plugin, "god_spear");
    }

    public void start() {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "god_spear_recipe");
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createGodSpear());
        recipe.shape("NEN", " B ", " S ");
        recipe.setIngredient('N', Material.NETHERITE_BLOCK);
        recipe.setIngredient('E', Material.NETHER_STAR);
        recipe.setIngredient('B', Material.BLAZE_ROD);
        recipe.setIngredient('S', Material.NETHERITE_INGOT);
        Bukkit.addRecipe(recipe);
        cooldownTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::updateCooldownBars, 1L, 5L);
    }

    public void stop() {
        if (cooldownTicker != null) cooldownTicker.cancel();
        for (BossBar bar : cooldownBars.values()) bar.removeAll();
        cooldownBars.clear();
    }

    private ItemStack createGodSpear() {
        ItemStack spear = new ItemStack(Material.NETHERITE_SPEAR);
        ItemMeta meta = spear.getItemMeta();
        meta.displayName(Component.text("✦ ASKYRIA GOD SPEAR ✦"));
        meta.lore(List.of(
            Component.text("A weapon forged for Askyria's strongest."),
            Component.text("Ability: God's Impact"),
            Component.text("Right-click to unleash a shockwave."),
            Component.text("Cooldown: 20 seconds")
        ));
        meta.getPersistentDataContainer().set(spearKey, PersistentDataType.BYTE, (byte) 1);
        spear.setItemMeta(meta);
        return spear;
    }

    private boolean isGodSpear(ItemStack item) {
        return item != null && item.getType() == Material.NETHERITE_SPEAR
            && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(spearKey, PersistentDataType.BYTE);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("Only operators can use /godspear."));
            return true;
        }
        Player target;
        if (args.length == 0 || args[0].equalsIgnoreCase("@s")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console usage: /godspear <player>"));
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
        target.getInventory().addItem(createGodSpear()).values().forEach(item ->
            target.getWorld().dropItemNaturally(target.getLocation(), item)
        );
        sender.sendMessage(Component.text("Gave " + target.getName() + " the Askyria God Spear."));
        return true;
    }

    @EventHandler
    public void onGodSpearUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isGodSpear(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            showCooldownBar(player);
            return;
        }
        cooldowns.put(player.getUniqueId(), now + COOLDOWN_MS);
        showCooldownBar(player);

        Vector direction = player.getEyeLocation().getDirection().normalize();
        org.bukkit.Location impact = player.getEyeLocation().add(direction.multiply(4.0));
        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 1, 0.2, 0.2, 0.2, 0);
        impact.getWorld().spawnParticle(Particle.SONIC_BOOM, impact, 1, 0, 0, 0, 0);
        impact.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, impact, 90, 1.4, 0.8, 1.4, 0.18);
        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.75f);
        impact.getWorld().playSound(impact, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.2f);

        for (Entity entity : impact.getWorld().getNearbyEntities(impact, IMPACT_RADIUS, IMPACT_RADIUS, IMPACT_RADIUS)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (target instanceof Player && !player.getWorld().getPVP()) continue;
            if (target.getLocation().distanceSquared(impact) > IMPACT_RADIUS * IMPACT_RADIUS) continue;
            target.damage(IMPACT_DAMAGE, player);
            Vector knockback = target.getLocation().toVector().subtract(impact.toVector());
            if (knockback.lengthSquared() < 0.01) knockback = direction.clone();
            knockback.normalize().multiply(1.35).setY(0.42);
            target.setVelocity(knockback);
        }
        player.sendMessage(Component.text("✦ GOD'S IMPACT unleashed!"));
    }

    @EventHandler
    public void onGodSpearHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isGodSpear(player.getInventory().getItemInMainHand())) return;
        event.setDamage(event.getDamage() + 4.0);
    }

    private void showCooldownBar(Player player) {
        BossBar bar = cooldownBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar("GOD'S IMPACT", BarColor.PURPLE, BarStyle.SOLID);
            created.addPlayer(player);
            return created;
        });
        bar.setVisible(true);
    }

    private void updateCooldownBars() {
        long now = System.currentTimeMillis();
        for (UUID id : List.copyOf(cooldownBars.keySet())) {
            Player player = Bukkit.getPlayer(id);
            BossBar bar = cooldownBars.get(id);
            Long readyAt = cooldowns.get(id);
            if (player == null || readyAt == null) {
                bar.removeAll();
                cooldownBars.remove(id);
                continue;
            }
            long remaining = Math.max(0L, readyAt - now);
            if (remaining <= 0L) {
                player.sendMessage(Component.text("God's Impact is ready!"));
                bar.removeAll();
                cooldownBars.remove(id);
                cooldowns.remove(id);
                continue;
            }
            long seconds = (remaining + 999L) / 1000L;
            bar.setTitle("GOD'S IMPACT • " + seconds + "s");
            bar.setColor(BarColor.PURPLE);
            bar.setProgress(Math.max(0.0, Math.min(1.0, (double) remaining / COOLDOWN_MS)));
        }
    }
}
