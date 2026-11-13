package com.askyria.essentials;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.npc.NPC;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;

public final class ShopNpcManager extends PacketListenerAbstract implements Listener {
    private record ShopNpc(String type, String name, String description, NamedTextColor color) {}

    private static final Map<String, ShopNpc> DEFINITIONS = Map.of(
        "armor", new ShopNpc("armor", "ARMOR", "Protection V • Netherite Gear", NamedTextColor.AQUA),
        "kits", new ShopNpc("kits", "KITS", "Combat Kits • Choose Your Loadout", NamedTextColor.GREEN),
        "mythical", new ShopNpc("mythical", "MYTHICAL WEAPONS", "Rare Abilities", NamedTextColor.LIGHT_PURPLE),
        "seller", new ShopNpc("seller", "SELLER", "Trade Valuables for Core", NamedTextColor.GOLD)
    );

    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(2_000_000);
    private final JavaPlugin plugin;
    private final ShopVillagerManager shops;
    private final NamespacedKey hologramKey;
    private final NamespacedKey hologramTypeKey;
    private final Map<String, NPC> npcs = new LinkedHashMap<>();
    private final Map<Integer, String> entityTypes = new java.util.concurrent.ConcurrentHashMap<>();

    public ShopNpcManager(JavaPlugin plugin, ShopVillagerManager shops) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.shops = shops;
        this.hologramKey = new NamespacedKey(plugin, "core_shop_npc_hologram");
        this.hologramTypeKey = new NamespacedKey(plugin, "core_shop_npc_type");
    }

    public void load() {
        removeOldHolograms();
        // Retire only the old shop villagers created by this plugin.
        shops.removeAll(Bukkit.getConsoleSender());
        var section = plugin.getConfig().getConfigurationSection("shop-npcs");
        if (section == null) return;
        for (String type : section.getKeys(false)) {
            if (!DEFINITIONS.containsKey(type)) continue;
            String worldName = plugin.getConfig().getString("shop-npcs." + type + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) continue;
            Location location = new Location(world,
                plugin.getConfig().getDouble("shop-npcs." + type + ".x"),
                plugin.getConfig().getDouble("shop-npcs." + type + ".y"),
                plugin.getConfig().getDouble("shop-npcs." + type + ".z"),
                (float) plugin.getConfig().getDouble("shop-npcs." + type + ".yaw"),
                0f);
            createNpc(type, location);
        }
        for (Player player : Bukkit.getOnlinePlayers()) spawnForPlayer(player);
    }

    public void spawnShopNpc(Player creator, String type) {
        ShopNpc definition = DEFINITIONS.get(type.toLowerCase(java.util.Locale.ROOT));
        if (definition == null) {
            creator.sendMessage(Component.text("Use armor, kits, mythical, or seller."));
            return;
        }
        Location location = creator.getLocation().clone();
        org.bukkit.util.Vector direction = location.getDirection().setY(0).normalize();
        if (direction.lengthSquared() == 0) direction = new org.bukkit.util.Vector(0, 0, 1);
        location.add(direction.multiply(2));
        location.setPitch(0);
        location.setYaw(creator.getLocation().getYaw() + 180f);
        plugin.getConfig().set("shop-npcs." + definition.type() + ".world", location.getWorld().getName());
        plugin.getConfig().set("shop-npcs." + definition.type() + ".x", location.getX());
        plugin.getConfig().set("shop-npcs." + definition.type() + ".y", location.getY());
        plugin.getConfig().set("shop-npcs." + definition.type() + ".z", location.getZ());
        plugin.getConfig().set("shop-npcs." + definition.type() + ".yaw", location.getYaw());
        plugin.saveConfig();
        createNpc(definition.type(), location);
        creator.sendMessage(Component.text("Created the " + definition.name() + " shop NPC."));
    }

    private void createNpc(String type, Location location) {
        removeNpc(type);
        ShopNpc definition = DEFINITIONS.get(type);
        UUID profileId = UUID.nameUUIDFromBytes(("CORE-SMP-SHOP-" + type).getBytes(StandardCharsets.UTF_8));
        UserProfile profile = new UserProfile(profileId, definition.name().toLowerCase(java.util.Locale.ROOT),
            ((AskyriaEssentials) plugin).getShopNpcSkin(type));
        int entityId = NEXT_ENTITY_ID.getAndIncrement();
        NPC npc = new NPC(profile, entityId, GameMode.SURVIVAL, null, definition.color(),
            Component.text("◆ ", definition.color()).decorate(TextDecoration.BOLD), null);
        npc.setLocation(SpigotConversionUtil.fromBukkitLocation(location));
        npcs.put(type, npc);
        entityTypes.put(entityId, type);
        spawnDescription(type, location);
        for (Player player : Bukkit.getOnlinePlayers()) spawnForPlayer(player);
    }

    public void refreshSkins() {
        for (String type : new ArrayList<>(npcs.keySet())) {
            String worldName = plugin.getConfig().getString("shop-npcs." + type + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) continue;
            Location location = new Location(world,
                plugin.getConfig().getDouble("shop-npcs." + type + ".x"),
                plugin.getConfig().getDouble("shop-npcs." + type + ".y"),
                plugin.getConfig().getDouble("shop-npcs." + type + ".z"),
                (float) plugin.getConfig().getDouble("shop-npcs." + type + ".yaw"),
                0f);
            createNpc(type, location);
        }
    }

    private void spawnDescription(String type, Location location) {
        ShopNpc definition = DEFINITIONS.get(type);
        TextDisplay display = location.getWorld().spawn(location.clone().add(0, 2.25, 0), TextDisplay.class);
        display.setPersistent(true);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setLineWidth(240);
        display.setText(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .serialize(Component.text(definition.description(), definition.color())
                .decorate(TextDecoration.BOLD)));
        display.getPersistentDataContainer().set(hologramKey, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(hologramTypeKey, PersistentDataType.STRING, type);
    }

    private void removeOldHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity instanceof TextDisplay display
                        && display.getPersistentDataContainer().has(hologramKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private void spawnForPlayer(Player player) {
        for (Map.Entry<String, NPC> entry : npcs.entrySet()) {
            Location location = SpigotConversionUtil.toBukkitLocation(player.getWorld(), entry.getValue().getLocation());
            String worldName = plugin.getConfig().getString("shop-npcs." + entry.getKey() + ".world");
            if (worldName != null && player.getWorld().getName().equals(worldName)) {
                Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
                if (channel != null) entry.getValue().spawn(channel);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> spawnForPlayer(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
        if (channel != null) for (NPC npc : npcs.values()) npc.despawn(channel);
        Bukkit.getScheduler().runTask(plugin, () -> spawnForPlayer(player));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
        if (channel != null) for (NPC npc : npcs.values()) npc.despawn(channel);
    }

    public void removeShopNpc(Player player, String type) {
        if (!DEFINITIONS.containsKey(type)) {
            player.sendMessage(Component.text("Use armor, kits, mythical, or seller."));
            return;
        }
        removeNpc(type);
        plugin.getConfig().set("shop-npcs." + type, null);
        plugin.saveConfig();
        player.sendMessage(Component.text("Removed the " + type + " shop NPC."));
    }

    private void removeNpc(String type) {
        NPC old = npcs.remove(type);
        if (old != null) {
            entityTypes.remove(old.getId());
            old.despawnAll();
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity instanceof TextDisplay display
                        && type.equals(display.getPersistentDataContainer().get(hologramTypeKey, PersistentDataType.STRING))) {
                    display.remove();
                }
            }
        }
    }

    public void removeAll(Player player) {
        for (String type : new ArrayList<>(DEFINITIONS.keySet())) removeNpc(type);
        plugin.getConfig().set("shop-npcs", null);
        plugin.saveConfig();
        player.sendMessage(Component.text("Removed all CORE shop NPCs."));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        String type = entityTypes.get(packet.getEntityId());
        if (type == null) return;
        event.setCancelled(true);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT
                && packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) shops.openShop(player, type);
        });
    }
}
