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
        // Resolve a real surface target first. The orbital marker falls from above
        // and the payload detonates only when it reaches the ground.
        int x = target.getBlockX();
        int z = target.getBlockZ();
        int groundY = world.getHighestBlockYAt(x, z);
        Location ground = new Location(world, x + 0.5, groundY + 1.0, z + 0.5);
        int skyY = Math.min(world.getMaxHeight() - 3, Math.max(groundY + 35, target.getBlockY() + 60));
        Location markerSpawn = new Location(world, x + 0.5, skyY, z + 0.5);

        world.playSound(ground, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.55f);
        warningBeam(world, ground, type);
        world.spawnParticle(type.equals("lunar") ? Particle.END_ROD : Particle.SOUL_FIRE_FLAME,
            markerSpawn, 100, 1.2, 2.0, 1.2, 0.03);

        TNTPrimed marker = spawnTnt(world, markerSpawn, 200, 0.0f, false, new Vector(0, -3.8, 0));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!marker.isValid() || marker.isDead() || !world.equals(marker.getWorld())) {
                task.cancel();
                return;
            }
            Location at = marker.getLocation();
            boolean landed = at.getY() <= groundY + 1.15
                || (marker.getVelocity().getY() <= 0
                    && at.getBlock().getRelative(0, -1, 0).getType().isSolid());
            if (!landed) return;

            marker.remove();
            task.cancel();
            detonatePayload(world, ground, type);
        }, 1L, 1L);
    }

    private void detonatePayload(World world, Location ground, String type) {
        int blasts;
        double radius;
        float power;
        Particle impactParticle;
        Sound impactSound;

        switch (type) {
            case "rain" -> {
                blasts = 180; radius = 28.0; power = 8.0f;
                impactParticle = Particle.FLAME; impactSound = Sound.ENTITY_GENERIC_EXPLODE;
            }
            case "stab" -> {
                blasts = 240; radius = 24.0; power = 9.0f;
                impactParticle = Particle.SOUL_FIRE_FLAME; impactSound = Sound.ENTITY_WITHER_BREAK_BLOCK;
            }
            case "nuke" -> {
                blasts = 360; radius = 60.0; power = 12.0f;
                impactParticle = Particle.EXPLOSION; impactSound = Sound.ENTITY_GENERIC_EXPLODE;
            }
            case "lunar" -> {
                blasts = 320; radius = 52.0; power = 11.0f;
                impactParticle = Particle.REVERSE_PORTAL; impactSound = Sound.ENTITY_GENERIC_EXPLODE;
            }
            default -> { return; }
        }

        // One impact frame: all blast points are triggered in the same server tick,
        // rather than raining TNT over several seconds. The payload is ground-locked.
        world.spawnParticle(Particle.EXPLOSION, ground, type.equals("nuke") ? 220 : 100,
            radius * 0.55, 1.8, radius * 0.55, 0.08);
        world.spawnParticle(impactParticle, ground.clone().add(0, 1, 0),
            type.equals("nuke") ? 700 : 350, radius * 0.7, 3.0, radius * 0.7, 0.08);
        world.spawnParticle(Particle.CLOUD, ground, type.equals("nuke") ? 650 : 300,
            radius * 0.8, 2.0, radius * 0.8, 0.07);
        world.playSound(ground, impactSound, type.equals("nuke") ? 10.0f : 6.0f,
            type.equals("lunar") ? 0.45f : 0.7f);

        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < blasts; i++) {
            double distance = radius * Math.sqrt((i + 0.5) / blasts);
            double angle = i * goldenAngle;
            Location blast = ground.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            int bx = blast.getBlockX();
            int bz = blast.getBlockZ();
            int by = world.getHighestBlockYAt(bx, bz);
            blast.setY(by + 0.5);
            // Create every explosion immediately on the terrain surface.
            world.createExplosion(blast, power, false, true);
        }

        // Real TNT barrage layered on top of the impact explosions.
        int tntCount;
        float tntYield;
        switch (type) {
            case "rain" -> { tntCount = 160; tntYield = 6.0f; }
            case "stab" -> { tntCount = 220; tntYield = 7.0f; }
            case "nuke" -> { tntCount = 450; tntYield = 8.0f; }
            case "lunar" -> { tntCount = 360; tntYield = 8.0f; }
            default -> { return; }
        }
        double tntAngleStep = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < tntCount; i++) {
            double distance = radius * Math.sqrt((i + 0.5) / tntCount);
            double angle = i * tntAngleStep + 0.35;
            int tx = ground.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int tz = ground.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int surfaceY = world.getHighestBlockYAt(tx, tz);
            Location tntLocation = new Location(world, tx + 0.5, surfaceY + 9.0, tz + 0.5);
            Vector drift = new Vector(Math.cos(angle) * 0.12, -0.55, Math.sin(angle) * 0.12);
            TNTPrimed payload = spawnTnt(world, tntLocation, 45, tntYield, false, drift);
            payload.setFuseTicks(45);
        }
        world.playSound(ground, Sound.ENTITY_TNT_PRIMED, 4.0f, 0.55f);
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
