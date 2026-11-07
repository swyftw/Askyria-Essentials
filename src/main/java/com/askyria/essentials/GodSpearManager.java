package com.askyria.essentials;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GodSpearManager implements Listener, CommandExecutor {
    private static final long COOLDOWN_MS = 20_000L;
    private static final long DOUBLE_CLICK_MS = 400L;
    private static final int BEAM_TICKS = 80;
    private static final double BEAM_RANGE = 28.0;
    private static final double BEAM_DAMAGE = 10.0;
    private final JavaPlugin plugin;
    private final NamespacedKey spearKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastRightClick = new HashMap<>();
    private final Map<UUID, BukkitTask> activeBeams = new HashMap<>();

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
        for (BukkitTask task : activeBeams.values()) task.cancel();
        activeBeams.clear();
        lastRightClick.clear();
    }

    private ItemStack createGodSpear() {
        ItemStack spear = new ItemStack(Material.NETHERITE_SPEAR);
        ItemMeta meta = spear.getItemMeta();
        meta.displayName(Component.text("SOUL REAPER"));
        meta.lore(List.of(
            Component.text("A weapon forged for Askyria's strongest."),
            Component.text("Ability: GOD'S IMPACT"),
            Component.text("Double right-click to channel a 4-second beam."),
            Component.text("Cooldown: 20 seconds")
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
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
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!isGodSpear(player.getInventory().getItemInMainHand())) return;
        if (activeBeams.containsKey(id)) {
            event.setCancelled(true);
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastRightClick.put(id, now);
        if (previous == null || now - previous > DOUBLE_CLICK_MS) return;
        lastRightClick.remove(id);
        event.setCancelled(true);
        long readyAt = cooldowns.getOrDefault(id, 0L);
        if (readyAt > now) {
            player.sendMessage(Component.text("GOD'S IMPACT cooldown: " + ((readyAt - now + 999L) / 1000L) + "s"));
            return;
        }
        cooldowns.put(id, now + COOLDOWN_MS);
        startBeam(player);
    }

    private void startBeam(Player player) {
        UUID id = player.getUniqueId();
        Set<UUID> hitEntities = new HashSet<>();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 1.35f);
        player.sendMessage(Component.text("GOD'S IMPACT — BEAM CHANNELING"));
        final int[] elapsed = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !isGodSpear(player.getInventory().getItemInMainHand()) || elapsed[0] >= BEAM_TICKS) {
                BukkitTask running = activeBeams.remove(id);
                if (running != null) running.cancel();
                return;
            }
            Location origin = player.getEyeLocation();
            Vector direction = origin.getDirection().normalize();
            RayTraceResult blockHit = player.getWorld().rayTraceBlocks(origin, direction, BEAM_RANGE, FluidCollisionMode.NEVER, true);
            double maxDistance = blockHit == null ? BEAM_RANGE
                : Math.max(0.0, origin.distance(blockHit.getHitPosition().toLocation(player.getWorld())) - 0.15);
            for (double distance = 0.4; distance <= maxDistance; distance += 0.35) {
                Location point = origin.clone().add(direction.clone().multiply(distance));
                player.getWorld().spawnParticle(Particle.END_ROD, point, 3, 0.035, 0.035, 0.035, 0.005);
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 2, 0.045, 0.045, 0.045, 0.01);
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.025, 0.025, 0.025, 0.001);
                for (Entity entity : player.getWorld().getNearbyEntities(point, 0.45, 0.45, 0.45)) {
                    if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
                    if (target instanceof Player && !player.getWorld().getPVP()) continue;
                    if (!hitEntities.add(target.getUniqueId())) continue;
                    target.damage(BEAM_DAMAGE, player);
                    target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1);
                }
            }
            if (blockHit != null && blockHit.getHitPosition() != null) {
                Location end = blockHit.getHitPosition().toLocation(player.getWorld());
                player.getWorld().spawnParticle(Particle.FLASH, end, 1);
            }
            if (elapsed[0] % 10 == 0) player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.45f, 1.8f);
            elapsed[0]++;
        }, 0L, 1L);
        activeBeams.put(id, task);
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
}