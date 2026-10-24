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
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
            "§7Single-use orbital weapon",
            "§fRight-click to designate your target",
            "§8Consumed on activation"
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

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.isOp()) {
            player.sendMessage(Component.text("Only operators can use Orbitals."));
            return;
        }

        // Throw to aim: the orbital does not activate until this projectile hits something.
        Snowball orb = player.launchProjectile(Snowball.class);
        orb.setVelocity(player.getLocation().getDirection().normalize().multiply(1.8));
        orb.setItem(new ItemStack(Material.SNOWBALL));
        orb.getPersistentDataContainer().set(orbitalKey, PersistentDataType.STRING, type);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!orb.isValid() || orb.isDead()) { task.cancel(); return; }
            orb.getWorld().spawnParticle(Particle.FLAME, orb.getLocation(), 4, 0.08, 0.08, 0.08, 0.01);
            orb.getWorld().spawnParticle(Particle.SMOKE, orb.getLocation(), 2, 0.04, 0.04, 0.04, 0.01);
        }, 1L, 1L);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);

        if (item.getAmount() <= 1) {
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
        } else item.setAmount(item.getAmount() - 1);
    }

    @EventHandler
    public void onOrbitalProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String type = projectile.getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
        if (type == null) return;
        Location hit = projectile.getLocation().clone();
        if (event.getHitBlock() != null) hit = event.getHitBlock().getLocation().add(0.5, 1, 0.5);
        projectile.remove();
        World world = projectile.getWorld();
        world.playSound(hit, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.6f);
        world.spawnParticle(Particle.END_ROD, hit.clone().add(0, 1, 0), 45, 1, 1, 1, 0.08);
        // Give the player time to see the marked target before the strike begins.
        // Capture a stable copy because hit may be reassigned when a block is hit.
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
        // RAIN: a dramatic, staggered meteor shower with smoke trails and impact bursts.
        int top = Math.min(world.getMaxHeight() - 8, target.getBlockY() + 72);
        for (int shot = 0; shot < 90; shot++) {
            final int n = shot;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double angle = n * 2.399963229728653;
                double radius = 5 + (n % 9) * 2.0;
                Location spawn = target.clone().add(Math.cos(angle) * radius,
                    Math.max(12, top - target.getY()), Math.sin(angle) * radius);
                world.spawnParticle(Particle.FLAME, spawn, 18, 0.45, 0.35, 0.45, 0.025);
                world.spawnParticle(Particle.CLOUD, spawn, 12, 0.4, 0.3, 0.4, 0.025);
                world.spawnParticle(Particle.ELECTRIC_SPARK, spawn, 8, 0.25, 0.25, 0.25, 0.02);
                world.playSound(spawn, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.45f + (n % 5) * 0.12f);
                spawnTnt(world, spawn, 36, 5.0f, false,
                    new Vector(-Math.cos(angle) * 0.18, -2.6, -Math.sin(angle) * 0.18));
            }, n * 2L);
        }
    }

    private void startStab(World world, Location target) {
        // STAB: focused compact TNT sphere repeatedly drilling the same location.
        int top = Math.min(world.getMaxHeight() - 12, target.getBlockY() + 95);
        final int waves = 5;
        final int perWave = 36;
        for (int wave = 0; wave < waves; wave++) {
            final int currentWave = wave;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location center = target.clone().add(0, Math.max(18, top - target.getY()) - currentWave * 12.0, 0);
                world.spawnParticle(Particle.FLAME, center, 80, 1.2, 1.0, 1.2, 0.035);
                world.spawnParticle(Particle.CLOUD, center, 100, 1.5, 1.0, 1.5, 0.035);
                world.spawnParticle(Particle.CRIT, center, 45, 0.9, 0.8, 0.9, 0.03);
                world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 3.0f, 0.45f + currentWave * 0.08f);
                for (int i = 0; i < perWave; i++) {
                    double angle = i * 2.399963229728653;
                    double radius = Math.sqrt(i / (double) perWave) * 2.8;
                    Location spawn = center.clone().add(Math.cos(angle) * radius, (i % 3) * 0.35, Math.sin(angle) * radius);
                    spawnTnt(world, spawn, 32, 6.0f, false, new Vector(0, -4.2, 0));
                }
            }, wave * 18L);
        }
    }

    private void startNuke(World world, Location target) {
        // NUKE: a giant expanding spiral of TNT that grows wider as it descends.
        final int count = 420;
        final int startY = Math.min(world.getMaxHeight() - 10, target.getBlockY() + 100);
        for (int i = 0; i < count; i++) {
            final int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double progress = n / (double) (count - 1);
                double angle = n * 0.32;
                double radius = 3.0 + progress * 65.0;
                double y = Math.max(8, startY - target.getY()) - progress * 65.0;
                Location spawn = target.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                world.spawnParticle(Particle.FLAME, spawn, 14, 0.45, 0.4, 0.45, 0.02);
                world.spawnParticle(Particle.ELECTRIC_SPARK, spawn, 8, 0.3, 0.3, 0.3, 0.02);
                world.spawnParticle(Particle.CLOUD, spawn, 6, 0.35, 0.3, 0.35, 0.02);
                spawnTnt(world, spawn, 40, 6.0f, false,
                    new Vector(-Math.cos(angle) * 0.2, -2.5, -Math.sin(angle) * 0.2));
            }, n / 3L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.EXPLOSION, target, 90, 14, 4, 14, 0.1);
            world.spawnParticle(Particle.CLOUD, target, 320, 24, 8, 24, 0.06);
            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 8.0f, 0.45f);
        }, 90L);
    }

    private void startLunar(World world, Location target) {
        // LUNAR: fill a sphere with TNT rather than only making a thin shell.
        final int points = 560;
        final double radius = 20.0;
        Location center = target.clone().add(0, Math.min(76, world.getMaxHeight() - target.getBlockY() - 22), 0);
        world.spawnParticle(Particle.END_ROD, center, 180, radius, radius, radius, 0.03);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 260, radius, radius, radius, 0.08);
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 4.0f, 0.5f);
        for (int i = 0; i < points; i++) {
            final int point = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
                double y = 1.0 - (2.0 * (point + 0.5) / points);
                double ring = Math.sqrt(1.0 - y * y);
                double theta = goldenAngle * point;
                double x = Math.cos(theta) * ring;
                double z = Math.sin(theta) * ring;
                // Distribute points throughout the volume for a filled sphere.
                double depth = Math.cbrt((point % 53 + 1) / 53.0);
                Location spawn = center.clone().add(x * radius * depth, y * radius * depth, z * radius * depth);
                spawnTnt(world, spawn, 44, 5.0f, false, new Vector(x * 0.1, -1.9, z * 0.1));
                world.spawnParticle(Particle.END_ROD, spawn, 2, 0.1, 0.1, 0.1, 0.01);
            }, point / 7L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.EXPLOSION, target, 45, 10, 3, 10, 0.1);
            world.spawnParticle(Particle.REVERSE_PORTAL, target, 300, 14, 5, 14, 0.12);
            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 8.0f, 0.55f);
        }, 65L);
    }

    private void spawnTnt(World world, Location location, int fuseTicks, float yield,
                          boolean incendiary, Vector velocity) {
        TNTPrimed tnt = world.spawn(location, TNTPrimed.class);
        tnt.setFuseTicks(fuseTicks);
        tnt.setYield(yield);
        tnt.setIsIncendiary(incendiary);
        tnt.setVelocity(velocity);
    }
}
