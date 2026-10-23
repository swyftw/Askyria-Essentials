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
            player.sendMessage(Component.text("You received all four Orbitals."));
        } else if (List.of("rain", "stab", "nuke", "lunar").contains(type)) {
            give(player, type);
            player.sendMessage(Component.text("You received the " + type.toUpperCase() + " Orbital."));
        } else {
            player.sendMessage(Component.text("Choose kit, rain, stab, nuke, or lunar."));
        }
        return true;
    }

    private void give(Player player, String type) {
        Material material = switch (type) {
            case "rain" -> Material.TRIDENT;
            case "stab" -> Material.IRON_SWORD;
            case "nuke" -> Material.TNT;
            case "lunar" -> Material.NETHER_STAR;
            default -> Material.FIRE_CHARGE;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(switch (type) {
            case "rain" -> "Rain Orbital";
            case "stab" -> "Stab Orbital";
            case "nuke" -> "Nuke Orbital";
            default -> "Lunar Orbital";
        }));
        meta.lore(List.of(Component.text("Right-click to call an orbital TNT strike.")));
        meta.getPersistentDataContainer().set(orbitalKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        player.getInventory().addItem(item).values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }

    @EventHandler
    public void onOrbitalUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String type = item.getItemMeta().getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
        if (type == null) return;
        event.setCancelled(true);
        if (!player.isOp()) {
            player.sendMessage(Component.text("Only operators can use Orbitals."));
            return;
        }
        Location target = player.getTargetBlockExact(100) == null
            ? player.getLocation().add(player.getLocation().getDirection().multiply(12))
            : player.getTargetBlockExact(100).getLocation().add(0.5, 1, 0.5);
        strike(player.getWorld(), target, type);
        if (item.getAmount() <= 1) item.setAmount(0); else item.setAmount(item.getAmount() - 1);
    }

    private void strike(World world, Location target, String type) {
        int count = switch (type) { case "rain" -> 18; case "stab" -> 8; case "nuke" -> 55; default -> 24; };
        int radius = switch (type) { case "rain" -> 5; case "stab" -> 1; case "nuke" -> 9; default -> 6; };
        if (type.equals("lunar")) {
            world.spawnParticle(Particle.END_ROD, target.clone().add(0, 12, 0), 120, 3, 2, 3, 0.08);
            world.playSound(target, Sound.BLOCK_BEACON_POWER_SELECT, 3.0f, 0.5f);
        } else {
            world.spawnParticle(Particle.CLOUD, target.clone().add(0, 8, 0), 70, 2, 1, 2, 0.05);
            world.playSound(target, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.8f);
        }
        for (int i = 0; i < count; i++) {
            final int shot = i;
            long delay = type.equals("stab") ? i * 2L : i * 3L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double dx = (Math.random() * 2 - 1) * radius;
                double dz = (Math.random() * 2 - 1) * radius;
                if (type.equals("stab")) { dx = (shot % 3 - 1) * 0.7; dz = (shot % 2 == 0 ? 0.4 : -0.4); }
                Location spawn = target.clone().add(dx, 18 + Math.random() * 10, dz);
                TNTPrimed tnt = world.spawn(spawn, TNTPrimed.class);
                tnt.setFuseTicks(45);
                tnt.setVelocity(new Vector(0, -0.65, 0));
                tnt.setYield(type.equals("nuke") ? 12.0f : type.equals("lunar") ? 7.0f : 5.0f);
                tnt.setIsIncendiary(type.equals("nuke"));
            }, delay);
        }
    }
}
