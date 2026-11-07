package com.askyria.essentials;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GodSpearManager implements Listener, CommandExecutor {
    private static final long COOLDOWN_MS = 20_000L;
    private static final double IMPACT_RADIUS = 4.5;
    private static final double IMPACT_DAMAGE = 8.0;
    private final JavaPlugin plugin;
    private final NamespacedKey spearKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> grantedDoubleJump = new HashSet<>();

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
    }

    public void stop() {
    }

    private ItemStack createGodSpear() {
        ItemStack spear = new ItemStack(Material.NETHERITE_SPEAR);
        ItemMeta meta = spear.getItemMeta();
        meta.displayName(Component.text("SOUL REAPER"));
        meta.lore(List.of(
            Component.text("A weapon forged for Askyria's strongest."),
            Component.text("Ability: God's Impact"),
            Component.text("Double-jump to unleash a shockwave."),
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
        sender.sendMessage(Component.text("Gave " + target.getName() + " Soul Reaper."));
        return true;
    }

    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (!isGodSpear(player.getInventory().getItemInMainHand())) return;
        if (!grantedDoubleJump.remove(player.getUniqueId())) return;
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            player.sendMessage(Component.text("God's Impact is on cooldown: " + ((readyAt - now + 999L) / 1000L) + "s"));
            return;
        }
        cooldowns.put(player.getUniqueId(), now + COOLDOWN_MS);

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
        player.sendMessage(Component.text("GOD'S IMPACT unleashed!"));
    }

    @EventHandler
    public void onGodSpearHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isGodSpear(player.getInventory().getItemInMainHand())) return;
        event.setDamage(event.getDamage() + 4.0);
    }

    public long getCooldownSeconds(Player player) {
        long remaining = cooldowns.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis();
        return Math.max(0L, (remaining + 999L) / 1000L);
    }

    @EventHandler
    public void onSelectGodSpear(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        boolean selectingSpear = isGodSpear(player.getInventory().getItem(event.getNewSlot()));
        if (selectingSpear && player.isOnGround() && !player.getAllowFlight() && !player.isFlying()) {
            player.setAllowFlight(true);
            grantedDoubleJump.add(player.getUniqueId());
        } else if (!selectingSpear && grantedDoubleJump.remove(player.getUniqueId()) && !player.isFlying()) {
            player.setAllowFlight(false);
        }
    }

    @EventHandler
    public void onGroundForDoubleJump(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        boolean holdingSpear = isGodSpear(player.getInventory().getItemInMainHand());
        if (holdingSpear && player.isOnGround() && !player.getAllowFlight() && !player.isFlying()) {
            player.setAllowFlight(true);
            grantedDoubleJump.add(player.getUniqueId());
        } else if (!holdingSpear && grantedDoubleJump.remove(player.getUniqueId()) && !player.isFlying()) {
            player.setAllowFlight(false);
        }
    }
}
