package com.askyria.essentials;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;

public final class OrbitalManager implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey orbitalKey;

    public OrbitalManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.orbitalKey = new NamespacedKey(plugin, "orbital_type");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use orbital commands.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("Only operators can use orbital commands."));
            return true;
        }

        int index = args.length > 0 && args[0].equalsIgnoreCase("@s") ? 1 : 0;
        if (args.length <= index) {
            player.sendMessage(Component.text("Usage: /orbital [@s] <kit|rain|stab|nuke|lunar>"));
            return true;
        }

        String type = args[index].toLowerCase();
        if (type.equals("kit")) {
            for (String orbital : List.of("rain", "stab", "nuke", "lunar")) give(player, orbital);
            player.sendMessage(Component.text("Orbital kit delivered: RAIN, STAB, NUKE, LUNAR."));
        } else if (List.of("rain", "stab", "nuke", "lunar").contains(type)) {
            give(player, type);
            player.sendMessage(Component.text(type.toUpperCase() + " Orbital delivered."));
        } else {
            player.sendMessage(Component.text("Choose kit, rain, stab, nuke, or lunar."));
        }
        return true;
    }

    private void give(Player player, String type) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        String name = switch (type) {
            case "rain" -> "§b§lRAIN ORBITAL";
            case "stab" -> "§c§lSTAB ORBITAL";
            case "nuke" -> "§4§lNUKE ORBITAL";
            default -> "§d§lLUNAR ORBITAL";
        };
        meta.setDisplayName(name);
        meta.setLore(List.of(
            "§7Reusable orbital weapon",
            "§fRight-click to throw at your target",
            "§aReusable — not consumed"
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.getPersistentDataContainer().set(orbitalKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        player.getInventory().addItem(item).values()
            .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }

    @EventHandler
    public void onOrbitalUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String type = item.getItemMeta().getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
        if (type == null) return;
        Player player = event.getPlayer();
        if (!player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Only operators can use Orbitals."));
        }
        // Vanilla casts the real fishing hook; the hook is tagged in PlayerFishEvent.
    }

    @EventHandler
    public void onOrbitalCast(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;
        Player player = event.getPlayer();
        org.bukkit.inventory.EquipmentSlot hand = event.getHand() == null
            ? org.bukkit.inventory.EquipmentSlot.HAND : event.getHand();
        ItemStack item = player.getInventory().getItem(hand);
        if (item == null || !item.hasItemMeta()) return;
        String type = item.getItemMeta().getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
        if (type == null) return;
        if (!player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Only operators can use Orbitals."));
            return;
        }
        FishHook hook = event.getHook();
        hook.getPersistentDataContainer().set(orbitalKey, PersistentDataType.STRING, type);
        hook.setPersistent(true);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
        player.sendMessage(Component.text(type.toUpperCase() + " Orbital launched. Your rod is reusable."));
    }

    @EventHandler
    public void onOrbitalHookLand(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.IN_GROUND
                && event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY
                && event.getState() != PlayerFishEvent.State.REEL_IN) return;
        FishHook hook = event.getHook();
        String type = hook.getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
        if (type == null) return;
        Location hit = hook.getLocation().clone();
        if (event.getState() == PlayerFishEvent.State.IN_GROUND) {
            hit = hit.getBlock().getLocation().add(0.5, 1, 0.5);
        }
        launchStrikeFromHook(hook, hit, type);
    }

    @EventHandler
    public void onOrbitalProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String type = projectile.getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
        if (type == null) return;
        Location hit = projectile.getLocation().clone();
        if (event.getHitBlock() != null) hit = event.getHitBlock().getLocation().add(0.5, 1, 0.5);
        launchStrikeFromHook(projectile, hit, type);
    }

    private void launchStrikeFromHook(Projectile projectile, Location hit, String type) {
        projectile.getPersistentDataContainer().remove(orbitalKey);
        projectile.remove();
        World world = projectile.getWorld();
        world.playSound(hit, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.6f);
        world.spawnParticle(Particle.END_ROD, hit.clone().add(0, 1, 0), 45, 1, 1, 1, 0.08);
        Location strikeTarget = hit.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> strike(world, strikeTarget, type), 40L);
    }

    private void strike(World world, Location target, String type) {
        target.setY(Math.max(world.getMinHeight() + 2, Math.min(target.getY(), world.getMaxHeight() - 10)));
        world.playSound(target, Sound.ENTITY_WITHER_SPAWN, 3.0f, 0.65f);
        world.spawnParticle(Particle.END_ROD, target.clone().add(0, 8, 0), 90, 1.5, 4, 1.5, 0.04);
        warningBeam(world, target, type);

        switch (type) {
            case "rain" -> startRain(world, target);
            case "stab" -> startStab(world, target);
            case "nuke" -> startNuke(world, target);
            case "lunar" -> startLunar(world, target);
            default -> { }
        }
    }

    private void warningBeam(World world, Location target, String type) {
        for (int y = 0; y <= 24; y += 2) {
            Location p = target.clone().add(0, y, 0);
            world.spawnParticle(type.equals("lunar") ? Particle.END_ROD : Particle.SOUL_FIRE_FLAME,
                p, 12, 0.25, 0.2, 0.25, 0.01);
        }
        world.spawnParticle(Particle.CLOUD, target, 70, 2, 0.2, 2, 0.03);
        world.playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
    }

    private void startRain(World world, Location target) {
        // RAIN: a much denser, longer meteor storm with overlapping lanes.
        int top = Math.min(world.getMaxHeight() - 8, target.getBlockY() + 100);
        final int shots = 180;
        for (int shot = 0; shot < shots; shot++) {
            final int n = shot;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double angle = n * 2.399963229728653;
                double radius = 4 + (n % 13) * 2.2;
                Location spawn = target.clone().add(Math.cos(angle) * radius,
                    Math.max(12, top - target.getY()), Math.sin(angle) * radius);
                world.spawnParticle(Particle.FLAME, spawn, 26, 0.55, 0.4, 0.55, 0.035);
                world.spawnParticle(Particle.CLOUD, spawn, 20, 0.5, 0.35, 0.5, 0.03);
                world.spawnParticle(Particle.ELECTRIC_SPARK, spawn, 12, 0.3, 0.3, 0.3, 0.025);
                world.playSound(spawn, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.4f + (n % 5) * 0.12f);
                spawnTnt(world, spawn, 34, 6.0f, false,
                    new Vector(-Math.cos(angle) * 0.22, -3.0, -Math.sin(angle) * 0.22));
            }, n);
        }
    }

    private void startStab(World world, Location target) {
        // Sustained 5x5 orbital spear from high altitude down close to bedrock.
        int topY = Math.min(world.getMaxHeight() - 12, target.getBlockY() + 100);
        int bottomY = world.getMinHeight() + 2;
        final int width = 5;
        final int layerSpacing = 4;
        final int ticksBetweenLayers = 2;
        int layer = 0;
        world.spawnParticle(Particle.END_ROD, target.clone().add(0, 24, 0), 160, 2.5, 14.0, 2.5, 0.035);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, target.clone().add(0, 30, 0), 220, 3.0, 18.0, 3.0, 0.04);
        world.playSound(target, Sound.BLOCK_BEACON_POWER_SELECT, 3.0f, 0.35f);
        for (int y = topY; y >= bottomY; y -= layerSpacing) {
            final int layerY = y;
            final int delay = layer++ * ticksBetweenLayers;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location layerCenter = new Location(world, target.getBlockX() + 0.5, layerY, target.getBlockZ() + 0.5);
                world.spawnParticle(Particle.FLAME, layerCenter, 65, 2.0, 0.45, 2.0, 0.035);
                world.spawnParticle(Particle.SMOKE, layerCenter, 45, 2.0, 0.45, 2.0, 0.03);
                world.spawnParticle(Particle.ELECTRIC_SPARK, layerCenter, 35, 2.0, 0.5, 2.0, 0.05);
                for (int x = -width / 2; x <= width / 2; x++) {
                    for (int z = -width / 2; z <= width / 2; z++) {
                        Location spawn = layerCenter.clone().add(x, 0, z);
                        spawnTnt(world, spawn, 45, 5.0f, false, new Vector(x * 0.035, -4.2, z * 0.035));
                    }
                }
                world.playSound(layerCenter, Sound.ENTITY_TNT_PRIMED, 1.4f, 0.45f);
            }, delay);
        }
        world.spawnParticle(Particle.CLOUD, target, 180, 3.5, 0.8, 3.5, 0.06);
        world.playSound(target, Sound.ENTITY_WITHER_SHOOT, 5.0f, 0.3f);
    }

    private void startNuke(World world, Location target) {
        // Restore the previous large-area orbital barrage.
        final int count = 900;
        final double maxRadius = 112.0;
        int startHeight = Math.min(125, world.getMaxHeight() - target.getBlockY() - 12);
        Location center = target.clone().add(0, Math.max(18, startHeight), 0);
        world.spawnParticle(Particle.FLAME, center, 500, maxRadius * 0.45, 8, maxRadius * 0.45, 0.04);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 300, maxRadius * 0.4, 10, maxRadius * 0.4, 0.03);
        world.spawnParticle(Particle.CLOUD, center, 600, maxRadius * 0.5, 12, maxRadius * 0.5, 0.04);
        world.playSound(target, Sound.ENTITY_WITHER_SPAWN, 8.0f, 0.35f);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.399963229728653;
            double radius = maxRadius * Math.sqrt((i + 0.5) / count);
            double y = (i % 7) * 1.5;
            Location spawn = target.clone().add(Math.cos(angle) * radius, startHeight + y, Math.sin(angle) * radius);
            Vector velocity = new Vector(-Math.cos(angle) * 0.35, -3.4, -Math.sin(angle) * 0.35);
            TNTPrimed tnt = spawnTnt(world, spawn, 120, 8.0f, false, velocity);
            // Nuke payload detonates on first ground contact instead of using a timed fuse.
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (!tnt.isValid() || tnt.isDead()) { task.cancel(); return; }
                Location at = tnt.getLocation();
                boolean touchingGround = at.getY() <= world.getMinHeight() + 1
                    || (tnt.getVelocity().getY() <= 0
                        && at.clone().subtract(0, 0.15, 0).getBlock().getRelative(0, -1, 0).getType().isSolid());
                if (touchingGround) {
                    tnt.setFuseTicks(1);
                    task.cancel();
                }
            }, 1L, 1L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.EXPLOSION, target, 240, 30, 7, 30, 0.14);
            world.spawnParticle(Particle.CLOUD, target, 650, 42, 14, 42, 0.1);
            world.spawnParticle(Particle.FLAME, target, 400, 35, 8, 35, 0.06);
            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 10.0f, 0.35f);
        }, 100L);
    }

    private void startLunar(World world, Location target) {
        // LUNAR: an even larger, denser filled sphere, released in quick batches
        // to keep the server scheduler responsive while still appearing packed.
        final int points = 1800;
        final double radius = 30.0;
        int height = Math.min(88, world.getMaxHeight() - target.getBlockY() - 24);
        Location center = target.clone().add(0, Math.max(12, height), 0);
        world.spawnParticle(Particle.END_ROD, center, 420, radius, radius, radius, 0.05);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 700, radius, radius, radius, 0.12);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 360, radius * 0.7, radius * 0.7, radius * 0.7, 0.04);
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 8.0f, 0.3f);
        for (int i = 0; i < points; i++) {
            final int point = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
                double y = 1.0 - (2.0 * (point + 0.5) / points);
                double ring = Math.sqrt(1.0 - y * y);
                double theta = goldenAngle * point;
                double x = Math.cos(theta) * ring;
                double z = Math.sin(theta) * ring;
                double depth = Math.cbrt((point + 0.5) / points);
                Location spawn = center.clone().add(x * radius * depth, y * radius * depth, z * radius * depth);
                spawnTnt(world, spawn, 48, 7.0f, false, new Vector(x * 0.08, -3.8, z * 0.08));
                world.spawnParticle(Particle.END_ROD, spawn, 3, 0.16, 0.16, 0.16, 0.015);
            }, point / 24L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.EXPLOSION, target, 180, 22, 6, 22, 0.12);
            world.spawnParticle(Particle.REVERSE_PORTAL, target, 650, 30, 10, 30, 0.16);
            world.spawnParticle(Particle.CLOUD, target, 500, 30, 8, 30, 0.08);
            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 10.0f, 0.35f);
        }, 100L);
    }

    private TNTPrimed spawnTnt(World world, Location location, int fuseTicks, float yield,
                                boolean incendiary, Vector velocity) {
        TNTPrimed tnt = world.spawn(location, TNTPrimed.class);
        tnt.setFuseTicks(fuseTicks);
        tnt.setYield(yield);
        tnt.setIsIncendiary(incendiary);
        tnt.setVelocity(velocity);
        return tnt;
    }
}
