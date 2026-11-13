package com.askyria.essentials;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

public final class ShopVillagerManager implements Listener {
    private final AskyriaEssentials plugin;
    private final NamespacedKey shopVillagerKey;

    public ShopVillagerManager(AskyriaEssentials plugin) {
        this.plugin = plugin;
        this.shopVillagerKey = new NamespacedKey(plugin, "core_shop_villager");
    }

    public boolean isShopVillager(Entity entity) {
        return entity instanceof Villager villager
            && villager.getPersistentDataContainer().has(shopVillagerKey, PersistentDataType.BYTE);
    }

    public void markShopVillager(Villager villager) {
        villager.getPersistentDataContainer().set(shopVillagerKey, PersistentDataType.BYTE, (byte) 1);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
    }

    public void removeTargeted(Player player) {
        var eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        Entity target = player.getWorld().rayTraceEntities(
            eye, direction, 6.0, 0.5,
            entity -> isShopVillager(entity)
        ) == null ? null : player.getWorld().rayTraceEntities(
            eye, direction, 6.0, 0.5,
            entity -> isShopVillager(entity)
        ).getHitEntity();

        if (target == null) {
            player.sendMessage(Component.text("Look directly at a custom shop villager within 6 blocks."));
            return;
        }
        target.remove();
        player.sendMessage(Component.text("Removed the shop villager."));
    }

    public void removeAll(CommandSender sender) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isShopVillager(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        sender.sendMessage(Component.text("Removed " + removed + " custom shop villager(s)."));
    }

    @EventHandler
    public void onShopVillagerDamage(EntityDamageEvent event) {
        if (isShopVillager(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onShopVillagerTarget(EntityTargetLivingEntityEvent event) {
        if (isShopVillager(event.getEntity())) event.setCancelled(true);
    }
}
