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
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        String type = item.getItemMeta().getPersistentDataContainer()
            .get(orbitalKey, PersistentDataType.STRING);
        if (type == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.isOp()) {
            player.sendMessage(Component.text("Only operators can use Orbitals."));
            return;
        }

        Location hit = player.getTargetBlockExact(100) == null
            ? player.getLocation().add(player.getLocation().getDirection().multiply(12))
            : player.getTargetBlockExact(100).getLocation().add(0.5, 1, 0.5);
        strike(player.getWorld(), hit, type);

        // Consume exactly one rod, regardless of stack size.
        if (item.getAmount() <= 1) {
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            item.setAmount(item.getAmount() - 1);
        }
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
        // Dense, staggered grid of TNT from near the world ceiling. Fuses are long enough
        // for the falling TNT to travel deep; spreading spawns over ticks limits server spikes.
        int top = world.getMaxHeight() - 8;
        int width = 19;
        int total = width * width;
        for (int i = 0; i < total; i++) {
            final int shot = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int gx = shot % width - width / 2;
                int gz = shot / width - width / 2;
                Location spawn = target.clone().add(gx * 1.6, Math.max(8, top - target.getBlockY()), gz * 1.6);
                spawnTnt(world, spawn, 240, 4.5f, false, new Vector(gx * 0.002, -0.2, gz * 0.002));
                world.spawnParticle(Particle.SMOKE, spawn, 5, 0.25, 0.3, 0.25, 0.01);
            }, i / 5L);
        }
    }

    private void startStab(World world, Location target) {
        // 10-block-wide packed column. Several TNT descend together in each layer,
        // making a focused drill rather than a scattered carpet.
        int top = world.getMaxHeight() - 8;
        int bottom = Math.max(world.getMinHeight() + 2, target.getBlockY() - 8);
        int layerCount = Math.min(28, Math.max(1, (top - bottom) / 10));
        for (int layer = 0; layer < layerCount; layer++) {
            final int currentLayer = layer;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double y = top - currentLayer * 10.0;
                if (y <= world.getMinHeight() + 2) return;
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        Location spawn = target.clone().add(x * 1.8, y - target.getY(), z * 1.8);
                        spawnTnt(world, spawn, 240, 4.0f, false, new Vector(0, -0.32, 0));
                    }
                }
                world.spawnParticle(Particle.CRIT, target.clone().add(0, y - target.getY(), 0),
                    18, 3.0, 0.3, 3.0, 0.05);
            }, layer * 3L);
        }
    }

    private void startNuke(World world, Location target) {
        // A single large-radius explosion gives the 150-block nuke its intended scale
        // without creating thousands of primed TNT entities at once.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.EXPLOSION, target, 80, 8, 3, 8, 0.1);
            world.spawnParticle(Particle.CLOUD, target, 450, 30, 10, 30, 0.08);
            world.spawnParticle(Particle.FLAME, target, 300, 25, 8, 25, 0.03);
            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 10.0f, 0.45f);
            world.createExplosion(target, 18.0f, true, true);
        }, 30L);
    }

    private void startLunar(World world, Location target) {
        // Two distinct 50-block-diameter TNT spheres descend from opposite sides.
        launchLunarSphere(world, target.clone().add(-28, 48, 0), target, 0L);
        launchLunarSphere(world, target.clone().add(28, 48, 0), target, 35L);
    }

    private void launchLunarSphere(World world, Location start, Location impact, long delay) {
        final int points = 220;
        for (int i = 0; i < points; i++) {
            final int point = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Fibonacci sphere distribution, radius 25 blocks (50-block diameter).
                double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
                double y = 1.0 - (2.0 * (point + 0.5) / points);
                double ring = Math.sqrt(1.0 - y * y);
                double theta = goldenAngle * point;
                double x = Math.cos(theta) * ring;
                double z = Math.sin(theta) * ring;
                Location spawn = start.clone().add(x * 25, y * 25, z * 25);
                spawnTnt(world, spawn, 100, 3.5f, false, impact.toVector().subtract(spawn.toVector()).normalize().multiply(0.65));
                world.spawnParticle(Particle.END_ROD, spawn, 2, 0.15, 0.15, 0.15, 0.01);
            }, delay + point / 10L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.EXPLOSION, impact, 35, 8, 3, 8, 0.1);
            world.spawnParticle(Particle.REVERSE_PORTAL, impact, 250, 12, 5, 12, 0.15);
            world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 8.0f, 0.55f);
        }, delay + 100L);
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
