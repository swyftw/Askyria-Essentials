package com.askyria.essentials;

import io.papermc.paper.event.player.AsyncChatEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.DisplaySlot;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AskyriaEssentials extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Set<String>> ownedRanks = new HashMap<>();
    private final Map<UUID, String> selectedRanks = new HashMap<>();
    private static final List<String> PLAYER_RANKS = List.of("BOOSTER", "VIP", "BOOSTER+", "VIP+", "CORE", "MEDIA", "CREATOR");
    private static final List<String> STAFF_RANKS = List.of("OWNER", "CO OWNER", "HEAD MANAGEMENT", "MANAGEMENT", "DEVELOPER", "SR ADMIN", "ADMIN", "MODERATOR", "JR MOD");
    private static final List<String> KIT_ORDER = List.of("STARTER", "BOOSTER", "VIP", "BOOSTER+", "VIP+", "CORE");
    private final Map<UUID, Component> tags = new HashMap<>();
    private final Map<UUID, String> rawTags = new HashMap<>();
    private final Map<UUID, String> rawIgnColors = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private final Map<UUID, Long> timedOutUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> timeoutReasons = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> combatFriends = new HashMap<>();
    private final Map<UUID, Set<UUID>> pendingCombatFriendRequests = new HashMap<>();
    private final Map<UUID, BukkitTask> spawnTeleportTasks = new HashMap<>();
    private final Map<UUID, Long> spawnCooldowns = new HashMap<>();
    private final Map<UUID, Scoreboard> cooldownOriginalBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> cooldownBoards = new HashMap<>();
    private final Map<UUID, Set<String>> cooldownSidebarEntries = new HashMap<>();
    private BukkitTask combatTicker;
    private BukkitTask tabTicker;
    private BukkitTask simulatedPlayersTicker;
    private boolean simulatedPlayersEnabled = false;
    private final List<String> simulatedPlayerNames = new ArrayList<>();
    private final Map<UUID, Set<UUID>> sentFakeEntries = new HashMap<>();
    private final Map<String, List<TextureProperty>> simulatedPlayerSkins = new HashMap<>();
    private volatile List<List<TextureProperty>> randomSkinPool = List.of();
    private PacketListenerAbstract fakePlayerPacketListener;
    private volatile List<String> statusPlayerNames = List.of();
    private volatile int statusOnlineCount = 0;
    // Skins used by simulated TAB player entries.
    private static final List<String> SKIN_SOURCE_ACCOUNTS = List.of(
        "Dream", "Technoblade", "Grian", "MumboJumbo", "GoodTimesWithScar",
        "CaptainSparklez", "DanTDM", "Stampy", "Aphmau", "LDShadowLady",
        "PrestonPlayz", "jeb_", "Notch", "Dinnerbone", "Grumm"
    );
    private static final List<String> SIMULATED_NAME_POOL = List.of(
        "lucass67", "DragonMoewww", "xXShadow418", "ItsNour23",
        "VoidCrafter", "M7mdPlays", "EnderWolf92", "CreeperToast",
        "LunarKite", "PixelNomad", "RavenBuilds", "ZaidCraft",
        "QuartzFox", "BlazeOrbit", "NoraPlaysMC", "CraftedByJay",
        "FrostByte77", "MangoMiner", "SilentBirch", "KokoBlocks"
    );
    private final MiniMessage mini = MiniMessage.miniMessage();
    @Override
    public void onEnable() {
        // Remove retired Askyria custom weapon recipes from the server recipe registry.
        Bukkit.removeRecipe(new org.bukkit.NamespacedKey(this, "god_mace"));
        Bukkit.removeRecipe(new org.bukkit.NamespacedKey(this, "god_spear_recipe"));
        saveDefaultConfig();
        long now = System.currentTimeMillis();
        if (getConfig().isConfigurationSection("protected-players")) {
            for (String key : getConfig().getConfigurationSection("protected-players").getKeys(false)) {
                try {
                    long expiry = getConfig().getLong("protected-players." + key);
                    if (expiry > now) protectedUntil.put(UUID.fromString(key), expiry);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (getConfig().isConfigurationSection("player-tags")) {
            for (String key : getConfig().getConfigurationSection("player-tags").getKeys(false)) {
                String raw = getConfig().getString("player-tags." + key);
                if (raw != null) {
                    try {
                        UUID id = UUID.fromString(key);
                        rawTags.put(id, raw);
                        applyTagToOnlinePlayer(id, raw);
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        }
        if (getConfig().isConfigurationSection("ign-colors")) {
            for (String key : getConfig().getConfigurationSection("ign-colors").getKeys(false)) {
                String raw = getConfig().getString("ign-colors." + key);
                if (raw != null) { try { rawIgnColors.put(UUID.fromString(key), raw); } catch (IllegalArgumentException ignored) { } }
            }
        }
        if (getConfig().isConfigurationSection("timeouts")) {
            for (String key : getConfig().getConfigurationSection("timeouts").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    long expiry = getConfig().getLong("timeouts." + key + ".until");
                    if (expiry > now) {
                        timedOutUntil.put(id, expiry);
                        timeoutReasons.put(id, getConfig().getString("timeouts." + key + ".reason", "No reason provided"));
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (getConfig().isConfigurationSection("player-ranks")) {
            for (String key : getConfig().getConfigurationSection("player-ranks").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ownedRanks.put(id, new HashSet<>(getConfig().getStringList("player-ranks." + key)));
                    String selected = getConfig().getString("selected-ranks." + key);
                    if (selected != null && ownedRanks.get(id).contains(selected)) selectedRanks.put(id, selected);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        loadCombatFriendData();
        Bukkit.getPluginManager().registerEvents(this, this);
        // JavaPlugin does not automatically route declared commands to onCommand
        // unless this class implements CommandExecutor. Register every declared
        // command explicitly so all CORE commands are executable and tab-completable.
        for (String commandName : getDescription().getCommands().keySet()) {
            org.bukkit.command.PluginCommand registeredCommand = getCommand(commandName);
            if (registeredCommand != null) {
                registeredCommand.setExecutor(this);
                registeredCommand.setTabCompleter(this);
            }
        }
        Bukkit.getScheduler().runTask(this, this::cleanupRetiredFeatureData);
        fakePlayerPacketListener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketSend(com.github.retrooper.packetevents.event.PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Status.Server.RESPONSE) return;
                WrapperStatusServerResponse wrapper = new WrapperStatusServerResponse(event);
                JsonObject response = wrapper.getComponent();
                JsonObject players = response.has("players") && response.get("players").isJsonObject()
                    ? response.getAsJsonObject("players") : new JsonObject();
                players.addProperty("max", Bukkit.getMaxPlayers());
                players.addProperty("online", statusOnlineCount);
                JsonArray sample = new JsonArray();
                int added = 0;
                for (String name : statusPlayerNames) {
                    if (added >= 12) break;
                    JsonObject profile = new JsonObject();
                    profile.addProperty("name", name);
                    profile.addProperty("id", fakeProfileId(name).toString());
                    sample.add(profile);
                    added++;
                }
                players.add("sample", sample);
                response.add("players", players);
                wrapper.setComponent(response);
                event.setLastUsedWrapper(wrapper);
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(fakePlayerPacketListener);
        loadRandomSkinPool();
        OrbitalManager orbitalManager = new OrbitalManager(this);
        Bukkit.getPluginManager().registerEvents(orbitalManager, this);
        getCommand("orbital").setExecutor(orbitalManager);
        combatTicker = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long nowTick = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isInSpawnProtection(player.getLocation())) {
                    player.sendActionBar(Component.text("SPAWN PROTECTED", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                    continue;
                }
                if (isProtected(player)) {
                    Long protectionExpiry = protectedUntil.get(player.getUniqueId());
                    long seconds = protectionExpiry == null ? 0L
                        : Math.max(0L, (protectionExpiry - nowTick + 999L) / 1000L);
                    long minutes = seconds / 60L;
                    long remainder = seconds % 60L;
                    player.sendActionBar(Component.text("PROTECTED " + minutes + "m " + remainder + "s", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                    continue;
                }
                Long until = combatUntil.get(player.getUniqueId());
                if (until == null) continue;
                long seconds = Math.max(0L, (until - nowTick + 999L) / 1000L);
                if (seconds <= 0) {
                    combatUntil.remove(player.getUniqueId());
                    player.sendActionBar(Component.text("You are no longer in combat.", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                } else {
                    player.sendActionBar(Component.text("COMBAT " + seconds + "s", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
        }, 0L, 5L);
        tabTicker = Bukkit.getScheduler().runTaskTimer(this, this::updateTabLists, 20L, 100L);
        getLogger().info("Askyria Essentials enabled.");
    }

    @Override
    public void onDisable() {
        if (combatTicker != null) combatTicker.cancel();
        for (BukkitTask task : spawnTeleportTasks.values()) task.cancel();
        spawnTeleportTasks.clear();
        if (tabTicker != null) tabTicker.cancel();
        if (simulatedPlayersTicker != null) simulatedPlayersTicker.cancel();
        if (fakePlayerPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(fakePlayerPacketListener);
        }
        saveProtectionData();
        saveCombatFriendData();
        saveTimeoutData();
    }

    private void saveTimeoutData() {
        getConfig().set("timeouts", null);
        for (Map.Entry<UUID, Long> entry : timedOutUntil.entrySet()) {
            String base = "timeouts." + entry.getKey();
            getConfig().set(base + ".until", entry.getValue());
            getConfig().set(base + ".reason", timeoutReasons.getOrDefault(entry.getKey(), "No reason provided"));
        }
        saveConfig();
    }

    private int staffLevel(UUID id) {
        Set<String> ranks = ownedRanks.getOrDefault(id, Set.of());
        for (int i = 0; i < STAFF_RANKS.size(); i++) if (ranks.contains(STAFF_RANKS.get(i))) return i;
        return -1;
    }

    private boolean hasStaffRank(Player player) {
        if (player.isOp() || player.hasPermission("askyria.timeout")) return true;
        Set<String> ranks = ownedRanks.getOrDefault(player.getUniqueId(), Set.of());
        return STAFF_RANKS.stream().anyMatch(ranks::contains);
    }

    private boolean hasStaffRank(UUID id) {
        return ownedRanks.getOrDefault(id, Set.of()).stream().anyMatch(STAFF_RANKS::contains);
    }

    private boolean canTempBan(Player player) {
        int level = staffLevel(player.getUniqueId());
        return level >= 0 && level <= STAFF_RANKS.indexOf("ADMIN");
    }

    private boolean canKick(Player player) {
        int level = staffLevel(player.getUniqueId());
        return level >= 0 && level <= STAFF_RANKS.indexOf("MODERATOR");
    }

    @EventHandler
    public void onTimedOutChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Long expiry = timedOutUntil.get(id);
        if (expiry == null) return;
        if (expiry <= System.currentTimeMillis()) {
            timedOutUntil.remove(id);
            timeoutReasons.remove(id);
            Bukkit.getScheduler().runTask(this, this::saveTimeoutData);
            return;
        }
        event.setCancelled(true);
        long seconds = Math.max(1L, (expiry - System.currentTimeMillis() + 999L) / 1000L);
        Bukkit.getScheduler().runTask(this, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(Component.text(
                "You are timed out for " + seconds + "s. Reason: " +
                timeoutReasons.getOrDefault(id, "No reason provided"),
                net.kyori.adventure.text.format.NamedTextColor.RED));
        });
    }

    private void loadCombatFriendData() {
        loadUuidRelations("combat-friends", combatFriends);
        loadUuidRelations("combat-friend-requests", pendingCombatFriendRequests);
    }

    private void loadUuidRelations(String path, Map<UUID, Set<UUID>> destination) {
        var section = getConfig().getConfigurationSection(path);
        if (section == null) return;
        for (String ownerKey : section.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(ownerKey);
                Set<UUID> related = new HashSet<>();
                for (String relatedKey : getConfig().getStringList(path + "." + ownerKey)) {
                    try { related.add(UUID.fromString(relatedKey)); }
                    catch (IllegalArgumentException ignored) { }
                }
                if (!related.isEmpty()) destination.put(owner, related);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveCombatFriendData() {
        getConfig().set("combat-friends", null);
        for (Map.Entry<UUID, Set<UUID>> entry : combatFriends.entrySet()) {
            List<String> ids = entry.getValue().stream().map(UUID::toString).toList();
            getConfig().set("combat-friends." + entry.getKey(), ids);
        }
        getConfig().set("combat-friend-requests", null);
        for (Map.Entry<UUID, Set<UUID>> entry : pendingCombatFriendRequests.entrySet()) {
            List<String> ids = entry.getValue().stream().map(UUID::toString).toList();
            getConfig().set("combat-friend-requests." + entry.getKey(), ids);
        }
        saveConfig();
    }

    private boolean areCombatFriends(Player first, Player second) {
        UUID a = first.getUniqueId();
        UUID b = second.getUniqueId();
        return combatFriends.getOrDefault(a, Set.of()).contains(b)
            && combatFriends.getOrDefault(b, Set.of()).contains(a);
    }

    private void cleanupRetiredFeatureData() {
        // Remove persisted data belonging only to the retired features.
        getConfig().set("chat-filter", null);
        getConfig().set("death-restores", null);
        getConfig().set("shop-layout", null);
        getConfig().set("shop-npcs", null);
        saveConfig();

        // Clean already-loaded worlds now. The chunk-load listener below catches
        // retired shop entities in chunks that are loaded later.
        for (World world : Bukkit.getWorlds()) {
            removeRetiredShopEntities(world.getEntities());
        }
    }

    private void removeRetiredShopEntities(Iterable<Entity> entities) {
        org.bukkit.NamespacedKey shopVillagerKey =
            new org.bukkit.NamespacedKey(this, "core_shop_villager");
        org.bukkit.NamespacedKey shopNpcHologramKey =
            new org.bukkit.NamespacedKey(this, "core_shop_npc_hologram");
        for (Entity entity : entities) {
            var data = entity.getPersistentDataContainer();
            if (data.has(shopVillagerKey, PersistentDataType.BYTE)
                    || data.has(shopNpcHologramKey, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onChunkLoadCleanupRetiredShopEntities(ChunkLoadEvent event) {
        removeRetiredShopEntities(java.util.Arrays.asList(event.getChunk().getEntities()));
    }

    private void saveProtectionData() {
        getConfig().set("protected-players", null);
        for (Map.Entry<UUID, Long> entry : protectedUntil.entrySet()) {
            getConfig().set("protected-players." + entry.getKey(), entry.getValue());
        }
        saveConfig();
    }

    private boolean isProtected(Player player) {
        Long expiry = protectedUntil.get(player.getUniqueId());
        if (expiry == null) return false;
        if (expiry <= System.currentTimeMillis()) {
            protectedUntil.remove(player.getUniqueId());
            saveProtectionData();
            return false;
        }
        return true;
    }

    private void saveRanks(UUID id) {
        getConfig().set("player-ranks." + id, new ArrayList<>(ownedRanks.getOrDefault(id, Set.of())));
        String selected = selectedRanks.get(id);
        getConfig().set("selected-ranks." + id, selected);
        saveConfig();
    }

    private String normalizeRank(String value) {
        String rank = value.toUpperCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return switch (rank) {
            case "BOOSTER", "VIP", "BOOSTER+", "VIP+", "CORE", "MEDIA", "CREATOR" -> rank;
            case "OWNER", "COOWNER" -> "CO OWNER";
            case "HEADMANAGEMENT" -> "HEAD MANAGEMENT";
            case "MANAGEMENT" -> "MANAGEMENT";
            case "DEVELOPER" -> "DEVELOPER";
            case "SRADMIN" -> "SR ADMIN";
            case "ADMIN" -> "ADMIN";
            case "MODERATOR" -> "MODERATOR";
            case "JRMOD" -> "JR MOD";
            default -> null;
        };
    }

    private String rankTag(String rank) {
        return switch (rank) {
            case "BOOSTER" -> "<bold><gradient:#8A2BE2:#D8B4FE>[BOOSTER]</gradient></bold>";
            case "VIP" -> "<bold><gradient:#FFD700:#FFF176>[VIP]</gradient></bold>";
            case "BOOSTER+" -> "<bold><gradient:#4B0082:#A855F7>[BOOSTER+]</gradient></bold>";
            case "VIP+" -> "<bold><gradient:#FFD700:#FF8C00>[VIP+]</gradient></bold>";
            case "CORE" -> "<bold><gradient:#25282D:#555B64:#858B94>[CORE]</gradient></bold>";
            case "MEDIA" -> "<bold><gradient:#1E88E5:#64B5F6:#BBDEFB>[MEDIA]</gradient></bold>";
            case "CREATOR" -> "<bold><gradient:#C62828:#EF5350:#FFCDD2>[CREATOR]</gradient></bold>";
            case "OWNER" -> "<bold><gradient:#FF3030:#FFD700>[OWNER]</gradient></bold>";
            case "CO OWNER" -> "<bold><gradient:#FF5A36:#FFD700>[CO OWNER]</gradient></bold>";
            case "HEAD MANAGEMENT" -> "<bold><gradient:#E040FB:#7C4DFF>[HEAD MANAGEMENT]</gradient></bold>";
            case "MANAGEMENT" -> "<bold><gradient:#9C27B0:#E1BEE7>[MANAGEMENT]</gradient></bold>";
            case "DEVELOPER" -> "<bold><gradient:#00BCD4:#80DEEA>[DEVELOPER]</gradient></bold>";
            case "SR ADMIN" -> "<bold><gradient:#F44336:#FF9800>[SR ADMIN]</gradient></bold>";
            case "ADMIN" -> "<bold><gradient:#EF5350:#FFCDD2>[ADMIN]</gradient></bold>";
            case "MODERATOR" -> "<bold><gradient:#43A047:#C8E6C9>[MODERATOR]</gradient></bold>";
            case "JR MOD" -> "<bold><gradient:#26A69A:#B2DFDB>[JR MOD]</gradient></bold>";
            default -> null;
        };
    }

    private void applySelectedRank(Player player) {
        String rank = selectedRanks.get(player.getUniqueId());
        if (rank == null) {
            rawTags.remove(player.getUniqueId());
            getConfig().set("player-tags." + player.getUniqueId(), null);
            tags.remove(player.getUniqueId());
            applyIgnColor(player);
            return;
        }
        String raw = rankTag(rank);
        rawTags.put(player.getUniqueId(), raw);
        getConfig().set("player-tags." + player.getUniqueId(), raw);
        applyTagToOnlinePlayer(player.getUniqueId(), raw);
        saveConfig();
    }

    private void openRankMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, Component.text("CORE Rank Selection"));
        int slot = 10;
        for (String rank : PLAYER_RANKS) {
            if (!ownedRanks.getOrDefault(player.getUniqueId(), Set.of()).contains(rank)) continue;
            ItemStack item = new ItemStack(rank.equals("CORE") ? Material.NETHERITE_INGOT : Material.DIAMOND);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            meta.displayName(mini.deserialize(rankTag(rank)));
            meta.lore(List.of(Component.text(rank.equals(selectedRanks.get(player.getUniqueId()))
                ? "Currently selected" : "Click to select")));
            item.setItemMeta(meta);
            menu.setItem(slot++, item);
        }
        if (slot == 10) player.sendMessage(Component.text("You have no player ranks to choose yet."));
        else player.openInventory(menu);
    }

    @EventHandler
    public void onRankMenuClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(Component.text("CORE Rank Selection"))) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) return;
        String chosen = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(item.getItemMeta().displayName()).replace("[", "").replace("]", "").trim().toUpperCase(java.util.Locale.ROOT);
        String rank = normalizeRank(chosen);
        if (rank == null || !ownedRanks.getOrDefault(player.getUniqueId(), Set.of()).contains(rank)) return;
        selectedRanks.put(player.getUniqueId(), rank);
        applySelectedRank(player);
        saveRanks(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(Component.text("Your active rank is now " + rank + "."));
    }

    private static final String KIT_MENU_TITLE = "CORE SMP | RANK KITS";

    private void openKitsMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, Component.text(KIT_MENU_TITLE));

        ItemStack background = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        var backgroundMeta = background.getItemMeta();
        backgroundMeta.displayName(Component.text(" "));
        background.setItemMeta(backgroundMeta);

        ItemStack frame = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        var frameMeta = frame.getItemMeta();
        frameMeta.displayName(Component.text(" "));
        frame.setItemMeta(frameMeta);

        for (int slot = 0; slot < menu.getSize(); slot++) {
            menu.setItem(slot, background);
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) menu.setItem(slot, frame);
        }

        ItemStack header = new ItemStack(Material.NETHER_STAR);
        var headerMeta = header.getItemMeta();
        headerMeta.displayName(mini.deserialize("<bold><gradient:#a855f7:#60a5fa>CORE SMP RANK KITS</gradient></bold>"));
        headerMeta.lore(List.of(
            Component.text("Choose a kit unlocked by your rank.", net.kyori.adventure.text.format.NamedTextColor.GRAY),
            Component.text("Starter is available to everyone.", net.kyori.adventure.text.format.NamedTextColor.GRAY)
        ));
        header.setItemMeta(headerMeta);
        menu.setItem(4, header);

        // Give every rank kit its own recognizable icon.
        Material[] icons = {
            Material.CHEST,                 // STARTER
            Material.FIREWORK_ROCKET,       // BOOSTER
            Material.DIAMOND,               // VIP
            Material.BLAZE_POWDER,          // BOOSTER+
            Material.ENCHANTED_GOLDEN_APPLE,// VIP+
            Material.NETHER_STAR            // CORE
        };
        int[] slots = {11, 13, 15, 29, 31, 33};
        for (int i = 0; i < KIT_ORDER.size(); i++) {
            String rank = KIT_ORDER.get(i);
            Set<String> playerOwnedRanks = ownedRanks.getOrDefault(player.getUniqueId(), Set.of());
            // Operators may claim any rank kit; everyone else needs the matching player rank.
            boolean allowed = player.isOp() || rank.equals("STARTER") || playerOwnedRanks.contains(rank);
            long readyAt = getConfig().getLong("kit-cooldowns." + player.getUniqueId() + "." + rank, 0L);
            boolean cooling = readyAt > System.currentTimeMillis();

            ItemStack icon = new ItemStack(!allowed ? Material.BARRIER : icons[i]);
            var meta = icon.getItemMeta();
            String title = rank.equals("STARTER")
                ? "<bold><white>STARTER KIT</white></bold>"
                : (rankTag(rank) == null ? "<bold><white>" + rank + "</white></bold>" : rankTag(rank))
                    + " <dark_gray>•</dark_gray> <bold><white>KIT</white></bold>";
            meta.displayName(mini.deserialize(title));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(rank.equals("STARTER")
                ? "A basic kit for every player"
                : "Exclusive kit for " + rank, net.kyori.adventure.text.format.NamedTextColor.GRAY));
            lore.add(Component.text(" "));
            if (!allowed) {
                lore.add(Component.text("LOCKED", net.kyori.adventure.text.format.NamedTextColor.RED));
                lore.add(Component.text("You need the " + rank + " rank.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            } else if (cooling) {
                lore.add(Component.text("ON COOLDOWN", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                lore.add(Component.text("Available in " + formatRemaining(readyAt - System.currentTimeMillis()),
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("READY TO CLAIM", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                lore.add(Component.text("Click to claim this kit.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            }
            lore.add(Component.text(" "));
            lore.add(Component.text("Cooldown: " + kitCooldownHours(rank) + " hours", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            icon.setItemMeta(meta);
            menu.setItem(slots[i], icon);
        }

        ItemStack info = new ItemStack(Material.WRITABLE_BOOK);
        var infoMeta = info.getItemMeta();
        infoMeta.displayName(mini.deserialize("<bold><aqua>HOW TO USE</aqua></bold>"));
        infoMeta.lore(List.of(
            Component.text("Click an unlocked kit to claim it.", net.kyori.adventure.text.format.NamedTextColor.GRAY),
            Component.text("Locked kits require their matching rank.", net.kyori.adventure.text.format.NamedTextColor.GRAY),
            Component.text("Cooldowns vary by rank: 24 or 42 hours.", net.kyori.adventure.text.format.NamedTextColor.GRAY),
            Component.text("Cooldowns remain saved after restarts.", net.kyori.adventure.text.format.NamedTextColor.GRAY),
            Component.text("Only server operators can bypass rank requirements.", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
        ));
        info.setItemMeta(infoMeta);
        menu.setItem(49, info);

        player.openInventory(menu);
    }

    private int kitCooldownHours(String rank) {
        return switch (rank) {
            case "BOOSTER+", "VIP+", "CORE" -> 42;
            default -> 24;
        };
    }

    private long kitCooldownMillis(String rank) {
        return java.util.concurrent.TimeUnit.HOURS.toMillis(kitCooldownHours(rank));
    }

    private String formatRemaining(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    private ItemStack kitIcon(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(List.of(Component.text(description), Component.text("Click to receive")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack potion(PotionType type) {
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }

    private ItemStack gear(Material material, Enchantment main, int mainLevel, boolean mending) {
        ItemStack item = new ItemStack(material);
        if (main != null) item.addUnsafeEnchantment(main, mainLevel);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        if (mending) item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        return item;
    }

    private ItemStack rankArmor(Material material, int protection, boolean mending) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(Enchantment.PROTECTION, protection);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        if (mending) item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        return item;
    }

    private void addDiamondArmor(List<ItemStack> items, int protection, boolean mending) {
        items.add(rankArmor(Material.DIAMOND_HELMET, protection, mending));
        items.add(rankArmor(Material.DIAMOND_CHESTPLATE, protection, mending));
        items.add(rankArmor(Material.DIAMOND_LEGGINGS, protection, mending));
        items.add(rankArmor(Material.DIAMOND_BOOTS, protection, mending));
    }

    private void addMaxDiamondArmor(List<ItemStack> items) {
        ItemStack helmet = rankArmor(Material.DIAMOND_HELMET, 4, true);
        helmet.addUnsafeEnchantment(Enchantment.RESPIRATION, 3);
        helmet.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        items.add(helmet);

        items.add(rankArmor(Material.DIAMOND_CHESTPLATE, 4, true));

        ItemStack leggings = rankArmor(Material.DIAMOND_LEGGINGS, 4, true);
        leggings.addUnsafeEnchantment(Enchantment.SWIFT_SNEAK, 3);
        items.add(leggings);

        ItemStack boots = rankArmor(Material.DIAMOND_BOOTS, 4, true);
        boots.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 4);
        boots.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 3);
        boots.addUnsafeEnchantment(Enchantment.SOUL_SPEED, 3);
        items.add(boots);
    }

    private ItemStack maxSword(Material material) {
        ItemStack sword = gear(material, Enchantment.SHARPNESS, 5, true);
        sword.addUnsafeEnchantment(Enchantment.LOOTING, 3);
        sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
        sword.addUnsafeEnchantment(Enchantment.SWEEPING_EDGE, 3);
        return sword;
    }

    private ItemStack maxPickaxe(Material material) {
        ItemStack pickaxe = gear(material, Enchantment.EFFICIENCY, 5, true);
        pickaxe.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
        return pickaxe;
    }

    private ItemStack maxTool(Material material) {
        return gear(material, Enchantment.EFFICIENCY, 5, true);
    }

    private ItemStack rankBow(int power) {
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addUnsafeEnchantment(Enchantment.POWER, power);
        bow.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        if (power >= 5) {
            bow.addUnsafeEnchantment(Enchantment.MENDING, 1);
            bow.addUnsafeEnchantment(Enchantment.FLAME, 1);
            bow.addUnsafeEnchantment(Enchantment.PUNCH, 2);
        }
        return bow;
    }

    private void addTopTierSupplies(List<ItemStack> items) {
        items.add(new ItemStack(Material.COBWEB, 64));
        items.add(new ItemStack(Material.ENDER_PEARL, 64));
        items.add(new ItemStack(Material.BREEZE_ROD, 64));
        items.add(new ItemStack(Material.WATER_BUCKET));
        items.add(new ItemStack(Material.COOKED_BEEF, 64));
        items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
        items.add(new ItemStack(Material.GOLDEN_APPLE, 64));
        items.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
        items.add(new ItemStack(Material.ARROW, 64));
        items.add(potion(PotionType.STRONG_STRENGTH));
        items.add(potion(PotionType.STRONG_SWIFTNESS));
        items.add(potion(PotionType.LONG_FIRE_RESISTANCE));
    }

    private List<ItemStack> buildRankKit(String rank) {
        List<ItemStack> items = new ArrayList<>();
        switch (rank) {
            case "STARTER" -> {
                // Basic stone tools plus food for every player.
                items.add(new ItemStack(Material.STONE_PICKAXE));
                items.add(new ItemStack(Material.STONE_AXE));
                items.add(new ItemStack(Material.STONE_SHOVEL));
                items.add(new ItemStack(Material.STONE_HOE));
                items.add(new ItemStack(Material.COOKED_BEEF, 32));
                items.add(new ItemStack(Material.GOLDEN_CARROT, 16));
            }
            case "BOOSTER" -> {
                addDiamondArmor(items, 3, false);
                items.add(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3, false));
                items.add(gear(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3, false));
                items.add(gear(Material.DIAMOND_AXE, Enchantment.EFFICIENCY, 2, false));
                items.add(gear(Material.DIAMOND_SHOVEL, Enchantment.EFFICIENCY, 2, false));
                items.add(rankBow(2));
                items.add(new ItemStack(Material.ARROW, 32));
                items.add(new ItemStack(Material.ENDER_PEARL, 32));
                items.add(new ItemStack(Material.BREEZE_ROD, 16));
                items.add(new ItemStack(Material.WATER_BUCKET));
                items.add(new ItemStack(Material.COOKED_BEEF, 32));
                items.add(new ItemStack(Material.GOLDEN_CARROT, 16));
                items.add(new ItemStack(Material.GOLDEN_APPLE, 8));
                items.add(potion(PotionType.STRENGTH));
                items.add(potion(PotionType.SWIFTNESS));
                items.add(potion(PotionType.FIRE_RESISTANCE));
            }
            case "VIP" -> {
                addDiamondArmor(items, 4, false);
                items.add(gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 4, false));
                items.add(gear(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 4, false));
                items.add(gear(Material.DIAMOND_AXE, Enchantment.EFFICIENCY, 3, false));
                items.add(gear(Material.DIAMOND_SHOVEL, Enchantment.EFFICIENCY, 3, false));
                items.add(rankBow(3));
                items.add(new ItemStack(Material.ARROW, 64));
                items.add(new ItemStack(Material.ENDER_PEARL, 48));
                items.add(new ItemStack(Material.BREEZE_ROD, 24));
                items.add(new ItemStack(Material.WATER_BUCKET, 2));
                items.add(new ItemStack(Material.COOKED_BEEF, 64));
                items.add(new ItemStack(Material.GOLDEN_CARROT, 32));
                items.add(new ItemStack(Material.GOLDEN_APPLE, 16));
                items.add(potion(PotionType.STRENGTH));
                items.add(potion(PotionType.SWIFTNESS));
                items.add(potion(PotionType.FIRE_RESISTANCE));
            }
            case "BOOSTER+", "VIP+" -> {
                addMaxDiamondArmor(items);
                items.add(maxSword(Material.DIAMOND_SWORD));
                items.add(maxPickaxe(Material.DIAMOND_PICKAXE));
                items.add(maxTool(Material.DIAMOND_AXE));
                items.add(maxTool(Material.DIAMOND_SHOVEL));
                items.add(rankBow(5));
                addTopTierSupplies(items);
            }
            case "CORE" -> {
                addMaxDiamondArmor(items);
                items.add(maxSword(Material.NETHERITE_SWORD));
                items.add(maxPickaxe(Material.NETHERITE_PICKAXE));
                items.add(maxTool(Material.NETHERITE_AXE));
                items.add(maxTool(Material.NETHERITE_SHOVEL));
                items.add(rankBow(5));
                addTopTierSupplies(items);
            }
        }
        return items;
    }

    @EventHandler
    public void onKitsMenuClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(Component.text(KIT_MENU_TITLE))) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int[] slots = {11, 13, 15, 29, 31, 33};
        int index = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == event.getSlot()) {
                index = i;
                break;
            }
        }
        if (index < 0) return;

        String rank = KIT_ORDER.get(index);
        Set<String> playerOwnedRanks = ownedRanks.getOrDefault(player.getUniqueId(), Set.of());
        if (!player.isOp() && !rank.equals("STARTER") && !playerOwnedRanks.contains(rank)) {
            player.sendMessage(Component.text("You need the " + rank + " rank to claim this kit."));
            return;
        }

        String path = "kit-cooldowns." + player.getUniqueId() + "." + rank;
        long readyAt = getConfig().getLong(path, 0L);
        if (readyAt > System.currentTimeMillis()) {
            player.sendMessage(Component.text("That kit is on cooldown for "
                + formatRemaining(readyAt - System.currentTimeMillis()) + "."));
            return;
        }

        List<ItemStack> items = buildRankKit(rank);
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        getConfig().set(path, System.currentTimeMillis() + kitCooldownMillis(rank));
        saveConfig();
        player.closeInventory();
        player.sendMessage(Component.text(rank + " kit claimed. Cooldown: " + kitCooldownHours(rank) + " hours."));
    }

    @EventHandler
    public void onKitsMenuDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().title().equals(Component.text(KIT_MENU_TITLE))) {
            event.setCancelled(true);
        }
    }

    private void cancelSpawnTeleport(Player player, String reason) {
        BukkitTask task = spawnTeleportTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        if (reason != null) player.sendMessage(Component.text(reason));
    }

    private void beginSpawnTeleport(Player player) {
        UUID id = player.getUniqueId();
        if (spawnTeleportTasks.containsKey(id)) {
            player.sendMessage(Component.text("Your spawn teleport is already counting down."));
            return;
        }
        long now = System.currentTimeMillis();
        Long combatExpiry = combatUntil.get(id);
        if (combatExpiry != null && combatExpiry > now) {
            player.sendMessage(Component.text("You cannot teleport to spawn while in combat."));
            return;
        }
        Long cooldownExpiry = spawnCooldowns.get(id);
        if (!player.isOp() && cooldownExpiry != null && cooldownExpiry > now) {
            long seconds = Math.max(1L, (cooldownExpiry - now + 999L) / 1000L);
            player.sendMessage(Component.text("Wait " + seconds + "s before using /spawn again."));
            return;
        }

        Location startingLocation = player.getLocation().clone();
        player.sendMessage(Component.text("Teleporting to spawn in 5 seconds. Don't move or take damage."));
        final int[] secondsLeft = {5};
        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline()) {
                spawnTeleportTasks.remove(id);
                taskRef[0].cancel();
                return;
            }
            if (secondsLeft[0] <= 1) {
                spawnTeleportTasks.remove(id);
                taskRef[0].cancel();
                Location target = player.getWorld().getSpawnLocation();
                target.setYaw(player.getLocation().getYaw());
                target.setPitch(player.getLocation().getPitch());
                player.teleport(target);
                if (!player.isOp()) spawnCooldowns.put(id, System.currentTimeMillis() + 10_000L);
                player.sendMessage(Component.text("Teleported to spawn."));
                return;
            }
            secondsLeft[0]--;
            player.sendActionBar(Component.text("SPAWN IN " + secondsLeft[0] + "..."));
        }, 20L, 20L);
        spawnTeleportTasks.put(id, taskRef[0]);
    }

    @EventHandler
    public void onCombatTaggedTeleportIntoSpawn(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Long combatExpiry = combatUntil.get(player.getUniqueId());
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && combatExpiry != null && combatExpiry > System.currentTimeMillis()
                && !isInSpawnProtection(from) && isInSpawnProtection(to)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot enter spawn while in combat.",
                net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onSpawnTeleportMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Combat-tagged players cannot cross into the protected spawn area.
        // Players already inside can still leave normally.
        Long combatExpiry = combatUntil.get(player.getUniqueId());
        if (to != null && combatExpiry != null && combatExpiry > System.currentTimeMillis()
                && !isInSpawnProtection(from) && isInSpawnProtection(to)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot enter spawn while in combat.",
                net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        if (!spawnTeleportTasks.containsKey(player.getUniqueId())) return;
        if (to != null && (from.getWorld() != to.getWorld()
                || from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ())) {
            cancelSpawnTeleport(player, "Spawn teleport cancelled because you moved.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!getConfig().getBoolean("seen-player." + id, false)) {
            long expiry = System.currentTimeMillis() + 600_000L;
            protectedUntil.put(id, expiry);
            getConfig().set("seen-player." + id, true);
            getConfig().set("protected-players." + id, expiry);

            // One-time CORE SMP starter kit: food only.
            List<ItemStack> starterItems = List.of(
                new ItemStack(Material.COOKED_BEEF, 32),
                new ItemStack(Material.BREAD, 16)
            );
            Map<Integer, ItemStack> starterLeftovers =
                player.getInventory().addItem(starterItems.toArray(new ItemStack[0]));
            starterLeftovers.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));

            player.sendActionBar(Component.text("NEW PLAYER PROTECTION • 10:00"));
            saveConfig();
        } else if (isProtected(player)) {
            player.sendActionBar(Component.text("NEW PLAYER PROTECTION IS ACTIVE"));
        }
        if (selectedRanks.containsKey(id)) {
            applySelectedRank(player);
        } else {
            String raw = rawTags.get(id);
            if (raw != null) applyTagToOnlinePlayer(id, raw);
            applyIgnColor(player);
        }
        sentFakeEntries.put(id, Set.of());
        updateTabLists();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        Player player = event.getPlayer();
        cancelSpawnTeleport(player, null);
        sentFakeEntries.remove(player.getUniqueId());
        cooldownOriginalBoards.remove(player.getUniqueId());
        cooldownBoards.remove(player.getUniqueId());
        cooldownSidebarEntries.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(this, this::updateTabLists);
        Long until = combatUntil.remove(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            player.setHealth(0.0);
            Bukkit.broadcast(Component.text(player.getName() + " logged out during combat and was slain."));
        }
    }

    private void rotateSimulatedPlayers() {
        List<String> shuffled = new ArrayList<>(SIMULATED_NAME_POOL);
        java.util.Collections.shuffle(shuffled);
        int amount = 8 + java.util.concurrent.ThreadLocalRandom.current().nextInt(5);
        simulatedPlayerNames.clear();
        simulatedPlayerNames.addAll(shuffled.subList(0, amount));
        assignRandomSkins();
        updateTabLists();
    }

    private void startSimulatedPlayers() {
        simulatedPlayersEnabled = true;
        if (simulatedPlayersTicker != null) simulatedPlayersTicker.cancel();
        rotateSimulatedPlayers();
        simulatedPlayersTicker = Bukkit.getScheduler().runTaskTimer(
            this, this::rotateSimulatedPlayers, 216000L, 216000L
        );
    }

    private void stopSimulatedPlayers() {
        simulatedPlayersEnabled = false;
        if (simulatedPlayersTicker != null) {
            simulatedPlayersTicker.cancel();
            simulatedPlayersTicker = null;
        }
        simulatedPlayerNames.clear();
        updateTabLists();
    }

    private void loadRandomSkinPool() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            List<List<TextureProperty>> loaded = new ArrayList<>();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            for (String account : SKIN_SOURCE_ACCOUNTS) {
                try {
                    HttpRequest lookup = HttpRequest.newBuilder(URI.create(
                        "https://api.mojang.com/users/profiles/minecraft/" + account
                    )).timeout(Duration.ofSeconds(8)).header("User-Agent", "AskyriaEssentials/1.0").GET().build();
                    HttpResponse<String> lookupResponse = client.send(lookup, HttpResponse.BodyHandlers.ofString());
                    if (lookupResponse.statusCode() != 200) continue;
                    String uuid = JsonParser.parseString(lookupResponse.body()).getAsJsonObject().get("id").getAsString();
                    HttpRequest profile = HttpRequest.newBuilder(URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"
                    )).timeout(Duration.ofSeconds(8)).header("User-Agent", "AskyriaEssentials/1.0").GET().build();
                    HttpResponse<String> profileResponse = client.send(profile, HttpResponse.BodyHandlers.ofString());
                    if (profileResponse.statusCode() != 200) continue;
                    JsonArray properties = JsonParser.parseString(profileResponse.body()).getAsJsonObject().getAsJsonArray("properties");
                    List<TextureProperty> textures = new ArrayList<>();
                    for (int i = 0; i < properties.size(); i++) {
                        JsonObject property = properties.get(i).getAsJsonObject();
                        if (!"textures".equals(property.get("name").getAsString())) continue;
                        String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                        textures.add(new TextureProperty("textures", property.get("value").getAsString(), signature));
                    }
                    if (!textures.isEmpty()) loaded.add(List.copyOf(textures));
                } catch (Exception ex) {
                    getLogger().fine("Could not load a display skin from " + account + ": " + ex.getMessage());
                }
            }
            // Deduplicate by the actual signed skin texture so two source accounts
            // that share a default skin cannot produce duplicate-looking entries.
            java.util.LinkedHashMap<String, List<TextureProperty>> unique = new java.util.LinkedHashMap<>();
            for (List<TextureProperty> skin : loaded) {
                String textureKey = skin.stream()
                    .filter(property -> "textures".equals(property.getName()))
                    .map(TextureProperty::getValue)
                    .findFirst().orElse("");
                if (!textureKey.isEmpty()) unique.putIfAbsent(textureKey, skin);
            }
            Bukkit.getScheduler().runTask(this, () -> {
                randomSkinPool = List.copyOf(unique.values());
                assignRandomSkins();
                // Re-send profile entries so clients receive the skin textures after loading.
                sentFakeEntries.clear();
                updateTabLists();
                getLogger().info("Loaded " + randomSkinPool.size() + " random display skins.");
            });
        });
    }

    private void assignRandomSkins() {
        simulatedPlayerSkins.clear();
        if (randomSkinPool.isEmpty()) return;
        List<List<TextureProperty>> shuffledSkins = new ArrayList<>(randomSkinPool);
        java.util.Collections.shuffle(shuffledSkins);
        int skinCount = Math.min(simulatedPlayerNames.size(), shuffledSkins.size());
        for (int i = 0; i < skinCount; i++) {
            simulatedPlayerSkins.put(simulatedPlayerNames.get(i), shuffledSkins.get(i));
        }
        if (skinCount < simulatedPlayerNames.size()) {
            getLogger().warning("Only " + skinCount + " unique skins loaded for "
                + simulatedPlayerNames.size() + " display names; remaining names will use the default skin.");
        }
    }

    private UUID fakeProfileId(String name) {
        return UUID.nameUUIDFromBytes(("askyria-display-player:" + name.toLowerCase()).getBytes(StandardCharsets.UTF_8));
    }

    private void sendFakeEntries(Player viewer, List<String> names) {
        Set<UUID> previous = sentFakeEntries.getOrDefault(viewer.getUniqueId(), Set.of());
        Set<UUID> current = new HashSet<>();
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> additions = new ArrayList<>();
        for (String name : names) {
            UUID id = fakeProfileId(name);
            current.add(id);
            {
                UserProfile profile = new UserProfile(id, name,
                    simulatedPlayerSkins.getOrDefault(name, List.of()));
                additions.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    profile, true, 0, GameMode.SURVIVAL, Component.text(name), null, 0, true
                ));
            }
        }
        List<UUID> removals = previous.stream().filter(id -> !current.contains(id)).toList();
        if (!removals.isEmpty()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoRemove(removals));
        }
        if (!additions.isEmpty()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(
                    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_HAT
                ), additions
            ));
        }
        sentFakeEntries.put(viewer.getUniqueId(), current);
    }

    private void updateTabLists() {
        List<String> fakeNames = simulatedPlayersEnabled ? List.copyOf(simulatedPlayerNames) : List.of();
        List<String> allNames = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) allNames.add(online.getName());
        allNames.addAll(fakeNames);
        statusPlayerNames = List.copyOf(allNames);
        statusOnlineCount = allNames.size();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Component header = mini.deserialize(
                "<bold><white>CORE SMP</white></bold>\n" +
                "<white>PING:</white> <red>" + Math.max(0, player.getPing()) + "ms</red>"
            );
            Component footer = mini.deserialize(
                "<white>ONLINE PLAYERS:</white> <light_purple>" + statusOnlineCount + "</light_purple>\n" +
                "<#FF8C00><bold>/discord</bold></#FF8C00>"
            );
            player.sendPlayerListHeaderAndFooter(header, footer);
            sendFakeEntries(player, fakeNames);
        }
    }

    private void setStoredTag(Player player, String raw) {
        UUID id = player.getUniqueId();
        rawTags.put(id, raw);
        getConfig().set("player-tags." + id, raw);
        applyTagToOnlinePlayer(id, raw);
        saveConfig();
        updateTabLists();
    }

    private void clearStoredTag(Player player) {
        UUID id = player.getUniqueId();
        rawTags.remove(id);
        tags.remove(id);
        getConfig().set("player-tags." + id, null);

        // Remove this plugin's tag team from every scoreboard currently in use.
        String teamName = "ask_" + id.toString().replace("-", "").substring(0, 12);
        java.util.Set<Scoreboard> boards = new java.util.HashSet<>();
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boards.add(viewer.getScoreboard());
        }
        for (Scoreboard board : boards) {
            Team team = board.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                } else {
                    team.prefix(Component.empty());
                    team.suffix(Component.empty());
                }
            }
        }

        // Restore the player's normal display and TAB names immediately.
        applyIgnColor(player);
        player.playerListName(player.displayName());
        saveConfig();
        updateTabLists();
    }


    @EventHandler
    public void onInvisibleKillerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && killer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            event.deathMessage(null);
            Bukkit.broadcast(Component.text(victim.getName() + " was killed by an invisible player."));
        }
    }

    private boolean isWithinSpawnRadius(Location location, World.Environment environment) {
        if (location == null || location.getWorld() == null
                || location.getWorld().getEnvironment() != environment) return false;
        // Protect 40 blocks in each horizontal direction from the spawn center.
        // The protection is horizontal and applies at every Y.
        int halfSize = Math.max(0, getConfig().getInt("spawn-protection-half-size", 40));
        Location spawn = location.getWorld().getSpawnLocation();
        return Math.abs(location.getBlockX() - spawn.getBlockX()) <= halfSize
            && Math.abs(location.getBlockZ() - spawn.getBlockZ()) <= halfSize;
    }

    private boolean isInSpawnProtection(Location location) {
        return isWithinSpawnRadius(location, World.Environment.NORMAL);
    }

    // In the Nether, protect blocks only near that world's spawn. This does
    // not block portal travel, PvP, or movement.
    private boolean isInNetherBuildProtection(Location location) {
        return isWithinSpawnRadius(location, World.Environment.NETHER);
    }

    private boolean isInBuildProtection(Location location) {
        return isInSpawnProtection(location) || isInNetherBuildProtection(location);
    }

    private void denySpawnAction(Player player) {
        player.sendActionBar(Component.text("SPAWN IS PROTECTED", net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    @EventHandler
    public void onSpawnBlockBreak(BlockBreakEvent event) {
        if (!event.getPlayer().isOp() && isInBuildProtection(event.getBlock().getLocation())) {
            event.setCancelled(true);
            denySpawnAction(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpawnBlockPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().isOp() && isInBuildProtection(event.getBlock().getLocation())) {
            event.setCancelled(true);
            denySpawnAction(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpawnBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!event.getPlayer().isOp() && isInBuildProtection(event.getBlock().getRelative(event.getBlockFace()).getLocation())) {
            event.setCancelled(true);
            denySpawnAction(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpawnBucketFill(PlayerBucketFillEvent event) {
        if (!event.getPlayer().isOp() && isInBuildProtection(event.getBlock().getLocation())) {
            event.setCancelled(true);
            denySpawnAction(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpawnEntityExplosion(EntityExplodeEvent event) {
        // Filter affected blocks individually so TNT cannot damage protected blocks,
        // even when the TNT itself explodes outside the protected radius.
        event.blockList().removeIf(block -> isInBuildProtection(block.getLocation()));
    }

    @EventHandler
    public void onSpawnBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isInBuildProtection(block.getLocation()));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player damaged && spawnTeleportTasks.containsKey(damaged.getUniqueId())) {
            cancelSpawnTeleport(damaged, "Spawn teleport cancelled because you took damage.");
        }
        if (event.getEntity() instanceof Player player && isProtected(player)) event.setCancelled(true);
    }

    private Player attackerFrom(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player p) return p;
        return null;
    }

    private void tagCombat(Player player) {
        combatUntil.put(player.getUniqueId(), System.currentTimeMillis() + 30_000L);
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player spawnVictim = event.getEntity() instanceof Player p ? p : null;
        Player spawnAttacker = attackerFrom(event.getDamager());
        if ((spawnVictim != null && isInSpawnProtection(spawnVictim.getLocation()))
                || (spawnAttacker != null && isInSpawnProtection(spawnAttacker.getLocation()))) {
            event.setCancelled(true);
            if (spawnAttacker != null) denySpawnAction(spawnAttacker);
            return;
        }
        if (event.getEntity() instanceof Player victim && isProtected(victim)) {
            event.setCancelled(true);
            return;
        }
        Player attacker = attackerFrom(event.getDamager());
        if (attacker != null && event.getEntity() instanceof Player victim && attacker != victim) {
            if (isProtected(attacker)) {
                event.setCancelled(true);
                attacker.sendMessage(Component.text("You cannot attack players while your protection is active."));
                return;
            }
            if (!isProtected(victim) && !areCombatFriends(attacker, victim)) {
                tagCombat(attacker);
                tagCombat(victim);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Component tag = tags.get(event.getPlayer().getUniqueId());
        String ignRaw = rawIgnColors.get(event.getPlayer().getUniqueId());
        if (tag != null || ignRaw != null) {
            Component ign = ignRaw == null ? event.getPlayer().displayName() : mini.deserialize(ignRaw);
            event.renderer((source, sourceDisplayName, message, viewer) -> {
                Component name = ignRaw == null ? sourceDisplayName : ign;
                Component line = Component.empty();
                if (tag != null) line = line.append(tag).append(Component.space());
                return line.append(name).append(Component.text(": ")).append(message);
            });
        }
    }

    private boolean requireOp(CommandSender sender) {
        if (sender.isOp()) return true;
        sender.sendMessage(Component.text("Only operators can use this command."));
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "announcement" -> {
                if (!requireOp(sender)) return true;
                if (args.length < 2 || !args[0].equalsIgnoreCase("send")) {
                    sender.sendMessage(Component.text("Usage: /announcement send <message>"));
                    return true;
                }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
                if (message.isEmpty()) {
                    sender.sendMessage(Component.text("Enter an announcement message."));
                    return true;
                }
                if (message.length() > 256) {
                    sender.sendMessage(Component.text("Announcement must be 256 characters or fewer."));
                    return true;
                }
                Component announcement = mini.deserialize(message);
                net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                    Component.text("ANNOUNCEMENT", net.kyori.adventure.text.format.NamedTextColor.GOLD,
                        net.kyori.adventure.text.format.TextDecoration.BOLD),
                    announcement,
                    net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofSeconds(5),
                        java.time.Duration.ofMillis(1000)
                    )
                );
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showTitle(title);
                    online.playSound(online.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                }
                sender.sendMessage(Component.text("Announcement displayed to " + Bukkit.getOnlinePlayers().size() + " player(s)."));
                return true;
            }
            case "kick" -> {
                if (sender instanceof Player actor && !canKick(actor)) {
                    sender.sendMessage(Component.text("You need MODERATOR or a higher staff rank to use this command."));
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage(Component.text("Usage: /kick <player> [reason]"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(Component.text("That player is not online."));
                    return true;
                }
                if (sender instanceof Player actor) {
                    if (actor.equals(target)) {
                        sender.sendMessage(Component.text("You cannot kick yourself."));
                        return true;
                    }
                    int actorLevel = staffLevel(actor.getUniqueId());
                    int targetLevel = staffLevel(target.getUniqueId());
                    if (targetLevel >= 0 && (actorLevel < 0 || actorLevel >= targetLevel)) {
                        sender.sendMessage(Component.text("You cannot kick a staff member with an equal or higher rank."));
                        return true;
                    }
                }
                String reason = args.length > 1
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    : "Kicked by a staff member";
                target.kick(Component.text("You have been kicked from CORE SMP.\nReason: " + reason));
                sender.sendMessage(Component.text("KICKED • " + target.getName() + " • " + reason,
                    net.kyori.adventure.text.format.NamedTextColor.GREEN));
                return true;
            }
            case "tempban" -> {
                if (sender instanceof Player actor && !canTempBan(actor)) {
                    sender.sendMessage(Component.text("You need ADMIN or a higher staff rank to use this command."));
                    return true;
                }
                if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                    var banList = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
                    long now = System.currentTimeMillis();
                    List<org.bukkit.BanEntry> entries = new ArrayList<>(banList.getBanEntries());
                    entries.removeIf(entry -> entry.getExpiration() != null && entry.getExpiration().getTime() <= now);
                    sender.sendMessage(Component.text("ACTIVE TEMPBANS (" + entries.size() + ")", net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    if (entries.isEmpty()) sender.sendMessage(Component.text("No active bans."));
                    for (org.bukkit.BanEntry entry : entries) {
                        String expiry = entry.getExpiration() == null ? "Permanent" : formatRemaining(entry.getExpiration().getTime() - now);
                        sender.sendMessage(Component.text(entry.getTarget() + " • " + expiry + " • "
                            + (entry.getReason() == null ? "No reason provided" : entry.getReason())));
                    }
                    return true;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                    String targetName = args[1];
                    var banList = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
                    if (banList.getBanEntry(targetName) == null) {
                        sender.sendMessage(Component.text(targetName + " is not currently banned."));
                        return true;
                    }
                    banList.pardon(targetName);
                    sender.sendMessage(Component.text("TEMPBAN REMOVED • " + targetName, net.kyori.adventure.text.format.NamedTextColor.GREEN));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /tempban <player> <time> <reason>"));
                    sender.sendMessage(Component.text("Also: /tempban list OR /tempban remove <player>"));
                    sender.sendMessage(Component.text("Time examples: 30m, 2h, 7d, 1w, perm."));
                    return true;
                }
                String targetName = args[0];
                if (sender instanceof Player actor) {
                    if (actor.getName().equalsIgnoreCase(targetName)) {
                        sender.sendMessage(Component.text("You cannot tempban yourself."));
                        return true;
                    }
                    Player onlineTarget = Bukkit.getPlayerExact(targetName);
                    UUID targetId = onlineTarget != null ? onlineTarget.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
                    int actorLevel = staffLevel(actor.getUniqueId());
                    int targetLevel = staffLevel(targetId);
                    if (targetLevel >= 0 && (actorLevel < 0 || actorLevel >= targetLevel)) {
                        sender.sendMessage(Component.text("You cannot ban a staff member with an equal or higher rank."));
                        return true;
                    }
                }
                java.time.Duration duration = parseBanDuration(args[1]);
                if (duration == null) {
                    sender.sendMessage(Component.text("Invalid time. Use 30m, 2h, 7d, 1w, or perm."));
                    return true;
                }
                String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
                if (reason.isEmpty()) {
                    sender.sendMessage(Component.text("A ban reason is required."));
                    return true;
                }
                java.util.Date expires = duration.isZero() ? null : java.util.Date.from(java.time.Instant.now().plus(duration));
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(targetName, reason, expires, sender.getName());
                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                if (onlineTarget != null) onlineTarget.kick(Component.text("You are banned from CORE SMP. Reason: " + reason));
                sender.sendMessage(Component.text("TEMPBAN APPLIED • " + targetName + " • "
                    + (duration.isZero() ? "Permanent" : args[1]) + " • " + reason, net.kyori.adventure.text.format.NamedTextColor.GREEN));
                return true;
            }
            case "timeout" -> {
                if (sender instanceof Player actor && !hasStaffRank(actor)) {
                    sender.sendMessage(Component.text("You need a staff rank to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /timeout <player> <duration> [reason]"));
                    sender.sendMessage(Component.text("Durations: 30s, 10m, 2h, 1d (maximum 30d)."));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player must be online to start a timeout."));
                    return true;
                }
                if (sender instanceof Player actor) {
                    if (actor.equals(target)) {
                        sender.sendMessage(Component.text("You cannot timeout yourself."));
                        return true;
                    }
                    int actorLevel = staffLevel(actor.getUniqueId());
                    int targetLevel = staffLevel(target.getUniqueId());
                    if (targetLevel >= 0 && (actorLevel < 0 || actorLevel >= targetLevel)) {
                        sender.sendMessage(Component.text("You cannot timeout a staff member with an equal or higher rank."));
                        return true;
                    }
                }
                java.time.Duration duration = parseBanDuration(args[1]);
                if (duration == null || duration.isZero() || duration.compareTo(java.time.Duration.ofDays(30)) > 0) {
                    sender.sendMessage(Component.text("Invalid duration. Use 30s, 10m, 2h or 1d (maximum 30d)."));
                    return true;
                }
                String reason = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "No reason provided";
                long expiry = System.currentTimeMillis() + duration.toMillis();
                timedOutUntil.put(target.getUniqueId(), expiry);
                timeoutReasons.put(target.getUniqueId(), reason);
                saveTimeoutData();
                target.sendMessage(Component.text("CHAT TIMEOUT • " + args[1] + " • " + reason, net.kyori.adventure.text.format.NamedTextColor.RED));
                sender.sendMessage(Component.text("TIMEOUT APPLIED", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .append(Component.text(" • " + target.getName() + " • " + args[1] + " • " + reason)));
                return true;
            }
            case "untimeout" -> {
                if (sender instanceof Player actor && !hasStaffRank(actor)) {
                    sender.sendMessage(Component.text("You need a staff rank to use this command."));
                    return true;
                }
                if (args.length != 1) {
                    sender.sendMessage(Component.text("Usage: /untimeout <player>"));
                    return true;
                }
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                UUID targetId = target.getUniqueId();
                if (timedOutUntil.remove(targetId) == null) {
                    sender.sendMessage(Component.text(args[0] + " does not have an active timeout."));
                    return true;
                }
                timeoutReasons.remove(targetId);
                saveTimeoutData();
                if (target.isOnline()) target.getPlayer().sendMessage(Component.text("Your chat timeout has been removed.", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                sender.sendMessage(Component.text("TIMEOUT REMOVED • " + args[0], net.kyori.adventure.text.format.NamedTextColor.GREEN));
                return true;
            }
            case "timeoutlist" -> {
                if (sender instanceof Player actor && !hasStaffRank(actor)) {
                    sender.sendMessage(Component.text("You need a staff rank to use this command."));
                    return true;
                }
                long now = System.currentTimeMillis();
                timedOutUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
                timeoutReasons.keySet().retainAll(timedOutUntil.keySet());
                saveTimeoutData();
                sender.sendMessage(Component.text("ACTIVE CHAT TIMEOUTS (" + timedOutUntil.size() + ")", net.kyori.adventure.text.format.NamedTextColor.GOLD));
                if (timedOutUntil.isEmpty()) sender.sendMessage(Component.text("No players are currently timed out."));
                for (Map.Entry<UUID, Long> entry : timedOutUntil.entrySet()) {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    long seconds = Math.max(1L, (entry.getValue() - now + 999L) / 1000L);
                    sender.sendMessage(Component.text((name == null ? entry.getKey().toString() : name)
                        + " • " + formatRemaining(entry.getValue() - now) + " • "
                        + timeoutReasons.getOrDefault(entry.getKey(), "No reason provided")));
                }
                return true;
            }
            case "rank" -> {
                if (args.length == 0 || args[0].equalsIgnoreCase("choose")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Only players can choose their rank."));
                        return true;
                    }
                    if (args.length == 1) { openRankMenu(player); return true; }
                    if (args.length == 2) {
                        String rank = normalizeRank(args[1]);
                        if (rank == null || !PLAYER_RANKS.contains(rank)
                                || !ownedRanks.getOrDefault(player.getUniqueId(), Set.of()).contains(rank)) {
                            player.sendMessage(Component.text("You do not own that player rank."));
                            return true;
                        }
                        selectedRanks.put(player.getUniqueId(), rank);
                        applySelectedRank(player);
                        saveRanks(player.getUniqueId());
                        player.sendMessage(Component.text("Selected rank: " + rank));
                        return true;
                    }
                    sender.sendMessage(Component.text("Usage: /rank choose [player-rank]"));
                    return true;
                }
                if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove")) {
                    if (sender instanceof Player actor && !hasStaffRank(actor)) {
                        sender.sendMessage(Component.text("You need a staff rank to manage ranks."));
                        return true;
                    }
                    if (args.length != 3) {
                        sender.sendMessage(Component.text("Usage: /rank give <player> <rank> OR /rank remove <player> <rank>"));
                        return true;
                    }
                    String rank = normalizeRank(args[2]);
                    if (rank == null) {
                        sender.sendMessage(Component.text("Player ranks: BOOSTER, VIP, BOOSTER+, VIP+, CORE, MEDIA, CREATOR."));
                        sender.sendMessage(Component.text("Staff ranks: OWNER, CO OWNER, HEAD MANAGEMENT, MANAGEMENT, DEVELOPER, SR ADMIN, ADMIN, MODERATOR, JR MOD."));
                        return true;
                    }
                    org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    UUID id = target.getUniqueId();
                    Set<String> ranks = ownedRanks.computeIfAbsent(id, ignored -> new HashSet<>());
                    boolean giving = args[0].equalsIgnoreCase("give");
                    if (giving && !ranks.add(rank)) {
                        sender.sendMessage(Component.text(args[1] + " already has " + rank + "."));
                        return true;
                    }
                    if (!giving && !ranks.remove(rank)) {
                        sender.sendMessage(Component.text(args[1] + " does not have " + rank + "."));
                        return true;
                    }
                    // Granting a rank never changes the player's active tag.
                    // If their selected rank is removed, clear the selection but leave
                    // their current tag untouched until they explicitly use /rank choose.
                    if (!giving && rank.equals(selectedRanks.get(id))) {
                        selectedRanks.remove(id);
                    }
                    saveRanks(id);
                    if (target.isOnline() && selectedRanks.containsKey(id)) {
                        applySelectedRank(target.getPlayer());
                    }
                    String action = giving ? "GAVE" : "REMOVED";
                    sender.sendMessage(Component.text(action + " RANK • " + args[1] + " • " + rank, net.kyori.adventure.text.format.NamedTextColor.GREEN));
                    if (target.isOnline()) target.getPlayer().sendMessage(Component.text(giving
                        ? "You received the " + rank + " rank." : "Your " + rank + " rank was removed."));
                    return true;
                }
                sender.sendMessage(Component.text("Usage: /rank give <player> <rank> | /rank remove <player> <rank> | /rank choose"));
                return true;
            }
            case "kits" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can open the kits menu."));
                    return true;
                }
                openKitsMenu(player);
                return true;
            }
            case "addplayers" -> {
                if (!requireOp(sender)) return true;
                if (simulatedPlayersEnabled) {
                    sender.sendMessage(Component.text("Simulated activity display is already running."));
                    return true;
                }
                startSimulatedPlayers();
                sender.sendMessage(Component.text("Simulated activity display started. Names rotate every 3 hours."));
                return true;
            }
            case "removeplayers" -> {
                if (!requireOp(sender)) return true;
                if (!simulatedPlayersEnabled && simulatedPlayerNames.isEmpty()) {
                    sender.sendMessage(Component.text("Simulated activity display is already stopped."));
                    return true;
                }
                stopSimulatedPlayers();
                sender.sendMessage(Component.text("Simulated activity display stopped and cleared. Use /addplayers to restart it."));
                return true;
            }
            case "discord" -> {
                sender.sendMessage(mini.deserialize("<gold><bold>CORE SMP Discord:</bold></gold> <aqua><underlined>https://discord.gg/CyyhRqTZVE</underlined></aqua>"));
                return true;
            }
            case "spawn" -> {
                if (!requireOp(sender)) return true;
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use /spawn."));
                    return true;
                }
                player.teleport(player.getWorld().getSpawnLocation());
                player.sendMessage(Component.text("Teleported to spawn."));
                return true;
            }
            case "protection" -> {
                if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Only players can clear their own protection."));
                        return true;
                    }
                    if (protectedUntil.remove(player.getUniqueId()) != null) {
                        saveProtectionData();
                        player.sendMessage(Component.text("Your protection has been removed."));
                    } else player.sendMessage(Component.text("You do not have active protection."));
                    return true;
                }
                if (!requireOp(sender)) return true;
                if (args.length < 2 || args.length > 3) {
                    sender.sendMessage(Component.text("Usage: /protection give <player> [duration] OR /protection remove <player>"));
                    return true;
                }
                String action = args[0].toLowerCase(java.util.Locale.ROOT);
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player '" + args[1] + "' is not online."));
                    return true;
                }
                if (action.equals("give")) {
                    java.time.Duration duration = parseBanDuration(args.length == 3 ? args[2] : "5m");
                    if (duration == null || duration.isZero()) {
                        sender.sendMessage(Component.text("Use a positive duration such as 5m, 30m, 2h, or 1d."));
                        return true;
                    }
                    long expiry = System.currentTimeMillis() + duration.toMillis();
                    protectedUntil.put(target.getUniqueId(), expiry);
                    saveProtectionData();
                    sender.sendMessage(Component.text("Gave " + target.getName() + " protection for " + (args.length == 3 ? args[2] : "5m") + "."));
                    target.sendMessage(Component.text("You have been given temporary protection."));
                    return true;
                }
                if (action.equals("remove")) {
                    if (protectedUntil.remove(target.getUniqueId()) != null) {
                        saveProtectionData();
                        sender.sendMessage(Component.text("Removed " + target.getName() + "'s protection."));
                        target.sendMessage(Component.text("Your protection has been removed."));
                    } else sender.sendMessage(Component.text(target.getName() + " does not have active protection."));
                    return true;
                }
                sender.sendMessage(Component.text("Use /protection give <player> [duration] or /protection remove <player>."));
                return true;
            }
            case "igncolor" -> {
                if (!requireOp(sender)) return true;
                if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) { sender.sendMessage(Component.text("That player must be online.")); return true; }
                    rawIgnColors.remove(target.getUniqueId());
                    getConfig().set("ign-colors." + target.getUniqueId(), null);
                    applyIgnColor(target);
                    saveConfig();
                    sender.sendMessage(Component.text("Reset " + target.getName() + "'s username color."));
                    return true;
                }
                if (args.length != 5 || !args[1].equalsIgnoreCase("fade3")) { sender.sendMessage(Component.text("Usage: /igncolor <player> fade3 <color1> <color2> <color3>")); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage(Component.text("Player must be online.")); return true; }
                String c1=color(args[2]), c2=color(args[3]), c3=color(args[4]);
                if (c1==null || c2==null || c3==null) { sender.sendMessage(Component.text("Invalid color. Use red, blue, green, purple, yellow, aqua, pink, orange, white, black, gray.")); return true; }
                String raw="<gradient:"+c1+":"+c2+":"+c3+">"+target.getName()+"</gradient>";
                rawIgnColors.put(target.getUniqueId(), raw);
                getConfig().set("ign-colors."+target.getUniqueId(), raw);
                saveConfig(); applyIgnColor(target);
                sender.sendMessage(Component.text("Updated "+target.getName()+" username gradient."));
                return true;
            }
            case "combatfriend" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use combat friend commands."));
                    return true;
                }
                if (args.length < 1 || args.length > 2) {
                    player.sendMessage(Component.text("Usage: /combatfriend <add|accept|deny|remove|list> [player]"));
                    return true;
                }
                String action = args[0].toLowerCase(java.util.Locale.ROOT);
                if (action.equals("list")) {
                    if (args.length != 1) {
                        player.sendMessage(Component.text("Usage: /combatfriend list"));
                        return true;
                    }
                    Set<UUID> friends = combatFriends.getOrDefault(player.getUniqueId(), Set.of());
                    if (friends.isEmpty()) {
                        player.sendMessage(Component.text("You have no combat friends."));
                    } else {
                        List<String> names = new ArrayList<>();
                        for (UUID id : friends) {
                            String name = Bukkit.getOfflinePlayer(id).getName();
                            names.add(name == null ? id.toString() : name);
                        }
                        player.sendMessage(Component.text("Combat friends: " + String.join(", ", names)));
                    }
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(Component.text("Usage: /combatfriend " + action + " <player>"));
                    return true;
                }
                if (action.equals("add")) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage(Component.text("That player must be online to receive a request."));
                        return true;
                    }
                    if (target.equals(player)) {
                        player.sendMessage(Component.text("You cannot add yourself as a combat friend."));
                        return true;
                    }
                    UUID requesterId = player.getUniqueId();
                    UUID targetId = target.getUniqueId();
                    if (areCombatFriends(player, target)) {
                        player.sendMessage(Component.text("You are already combat friends with " + target.getName() + "."));
                        return true;
                    }
                    if (pendingCombatFriendRequests.getOrDefault(requesterId, Set.of()).contains(targetId)) {
                        player.sendMessage(Component.text(target.getName() + " has already sent you a request. Use /combatfriend accept " + target.getName() + "."));
                        return true;
                    }
                    pendingCombatFriendRequests.computeIfAbsent(targetId, ignored -> new HashSet<>()).add(requesterId);
                    saveCombatFriendData();
                    player.sendMessage(Component.text("Combat friend request sent to " + target.getName() + "."));
                    target.sendMessage(Component.text(player.getName() + " wants to be your combat friend. Use /combatfriend accept " + player.getName() + " or /combatfriend deny " + player.getName() + "."));
                    return true;
                }
                if (action.equals("accept") || action.equals("deny")) {
                    org.bukkit.OfflinePlayer requester = Bukkit.getOfflinePlayer(args[1]);
                    UUID requesterId = requester.getUniqueId();
                    Set<UUID> requests = pendingCombatFriendRequests.get(player.getUniqueId());
                    if (requests == null || !requests.remove(requesterId)) {
                        player.sendMessage(Component.text("You do not have a pending request from " + args[1] + "."));
                        return true;
                    }
                    if (requests.isEmpty()) pendingCombatFriendRequests.remove(player.getUniqueId());
                    if (action.equals("accept")) {
                        combatFriends.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(requesterId);
                        combatFriends.computeIfAbsent(requesterId, ignored -> new HashSet<>()).add(player.getUniqueId());
                        player.sendMessage(Component.text("You are now combat friends with " + args[1] + "."));
                        Player onlineRequester = Bukkit.getPlayer(requesterId);
                        if (onlineRequester != null) onlineRequester.sendMessage(Component.text(player.getName() + " accepted your combat friend request."));
                    } else {
                        player.sendMessage(Component.text("Denied " + args[1] + "'s combat friend request."));
                        Player onlineRequester = Bukkit.getPlayer(requesterId);
                        if (onlineRequester != null) onlineRequester.sendMessage(Component.text(player.getName() + " denied your combat friend request."));
                    }
                    saveCombatFriendData();
                    return true;
                }
                if (action.equals("remove")) {
                    org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    UUID targetId = target.getUniqueId();
                    Set<UUID> ownFriends = combatFriends.get(player.getUniqueId());
                    if (ownFriends == null || !ownFriends.remove(targetId)) {
                        player.sendMessage(Component.text(args[1] + " is not on your combat friend list."));
                        return true;
                    }
                    if (ownFriends.isEmpty()) combatFriends.remove(player.getUniqueId());
                    Set<UUID> theirFriends = combatFriends.get(targetId);
                    if (theirFriends != null) {
                        theirFriends.remove(player.getUniqueId());
                        if (theirFriends.isEmpty()) combatFriends.remove(targetId);
                    }
                    saveCombatFriendData();
                    player.sendMessage(Component.text("Removed " + args[1] + " from your combat friends."));
                    Player onlineTarget = Bukkit.getPlayer(targetId);
                    if (onlineTarget != null) onlineTarget.sendMessage(Component.text(player.getName() + " removed you as a combat friend."));
                    return true;
                }
                player.sendMessage(Component.text("Usage: /combatfriend <add|accept|deny|remove|list> [player]"));
                return true;
            }
            case "cheatban" -> {
                if (!requireOp(sender)) return true;
                if (args.length != 2) { sender.sendMessage(Component.text("Usage: /cheatban <player> <duration> (30m, 2h, 7d, perm)")); return true; }
                java.time.Duration duration=parseBanDuration(args[1]);
                if (duration==null) { sender.sendMessage(Component.text("Invalid duration. Use 30m, 2h, 7d, 1w, or perm.")); return true; }
                java.util.Date expires=duration.isZero()?null:java.util.Date.from(java.time.Instant.now().plus(duration));
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(args[0], "Cheating", expires, sender.getName());
                Player online=Bukkit.getPlayerExact(args[0]);
                if (online!=null) online.kick(Component.text("You are banned for cheating. Duration: "+args[1]));
                sender.sendMessage(Component.text("Banned "+args[0]+" for "+args[1]+"."));
                return true;
            }
            case "cleartag" -> {
                if (!requireOp(sender)) return true;
                if (args.length != 1) {
                    sender.sendMessage(Component.text("Usage: /cleartag <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(Component.text("That player must be online."));
                    return true;
                }
                clearStoredTag(target);
                sender.sendMessage(Component.text("Removed " + target.getName() + "'s tag."));
                return true;
            }
            case "playertag" -> {
                if (!requireOp(sender)) return true;
                if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
                    Player clearTarget = Bukkit.getPlayerExact(args[1]);
                    if (clearTarget == null) {
                        sender.sendMessage(Component.text("That player must be online."));
                        return true;
                    }
                    clearStoredTag(clearTarget);
                    sender.sendMessage(Component.text("Removed " + clearTarget.getName() + "'s tag."));
                    return true;
                }
                if (args.length != 3 && args.length != 6) {
                    sender.sendMessage(Component.text("Usage: /playertag <player> [OWNER] fade yellow yellow orange"));
                    sender.sendMessage(Component.text("Or: /playertag <player> [OWNER] fade3"));
                    sender.sendMessage(Component.text("Or: /playertag clear <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player '" + args[0] + "' is not online. Check spelling and join the server first."));
                    return true;
                }
                String tagText = args[1].toUpperCase();
                if (!tagText.startsWith("[")) tagText = "[" + tagText + "]";
                String c1, c2, c3;
                if (args.length == 3 && args[2].equalsIgnoreCase("fade3")) {
                    c1 = "#00FF00"; c2 = "#FFFFFF"; c3 = "#0000FF";
                } else if (args.length == 6 && args[2].equalsIgnoreCase("fade")) {
                    c1 = color(args[3]); c2 = color(args[4]); c3 = color(args[5]);
                } else {
                    sender.sendMessage(Component.text("Invalid format. Use /playertag " + args[0] + " [OWNER] fade yellow yellow orange"));
                    return true;
                }
                if (c1 == null || c2 == null || c3 == null) {
                    sender.sendMessage(Component.text("Colors: green, white, blue, red, purple, yellow, aqua, pink, orange, black, gray.")); return true;
                }
                String safeTag = tagText.replace("<", "").replace(">", "");
                String raw = "<bold><gradient:" + c1 + ":" + c2 + ":" + c3 + ">" + safeTag + "</gradient></bold>";
                // A manually assigned player tag takes priority over rank tags.
                // The player can switch back to an owned rank explicitly with /rank choose.
                selectedRanks.remove(target.getUniqueId());
                saveRanks(target.getUniqueId());
                rawTags.put(target.getUniqueId(), raw);
                getConfig().set("player-tags." + target.getUniqueId(), raw);
                saveConfig();
                applyTagToOnlinePlayer(target.getUniqueId(), raw);
                sender.sendMessage(Component.text("Set " + target.getName() + "'s tag to " + tagText + "."));
                return true;
            }
            case "invsee", "endersee" -> {
                if (!requireOp(sender)) return true;
                if (!(sender instanceof Player viewer)) {
                    sender.sendMessage(Component.text("Only players can use this command."));
                    return true;
                }
                if (args.length != 1) {
                    viewer.sendMessage(Component.text("Usage: /" + label + " <player>"));
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    target = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getName().equalsIgnoreCase(args[0]))
                        .findFirst().orElse(null);
                }
                if (target == null || !target.isOnline()) {
                    viewer.sendMessage(Component.text("That player must be online."));
                    return true;
                }
                if (target.equals(viewer)) {
                    viewer.sendMessage(Component.text("You cannot use this command on yourself."));
                    return true;
                }

                final Player inventoryTarget = target;
                final boolean enderChest = command.getName().equalsIgnoreCase("endersee");
                // Open on the next server tick and report the real exception to console
                // instead of leaving players with only Bukkit's generic error message.
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!viewer.isOnline() || !inventoryTarget.isOnline()) {
                        if (viewer.isOnline()) viewer.sendMessage(Component.text("The target is no longer online."));
                        return;
                    }
                    try {
                        Inventory inventory = enderChest
                            ? inventoryTarget.getEnderChest()
                            : inventoryTarget.getInventory();
                        viewer.openInventory(inventory);
                    } catch (Exception ex) {
                        getLogger().severe("Failed to open " + (enderChest ? "Ender Chest" : "player inventory")
                            + " for " + viewer.getName() + " viewing " + inventoryTarget.getName());
                        getLogger().log(java.util.logging.Level.SEVERE, "Inventory viewer error", ex);
                        viewer.sendMessage(Component.text("Could not open that inventory. Check the server console."));
                    }
                });
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("kick") && (!(sender instanceof Player p) || canKick(p)) && args.length == 1) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(java.util.Locale.ROOT).startsWith(args[0].toLowerCase(java.util.Locale.ROOT))
                        && (!(sender instanceof Player actor) || (staffLevel(online.getUniqueId()) < 0
                        || staffLevel(actor.getUniqueId()) < staffLevel(online.getUniqueId())))) {
                    suggestions.add(online.getName());
                }
            }
            return suggestions;
        }
        if (command.getName().equalsIgnoreCase("tempban") && (!(sender instanceof Player p) || canTempBan(p))) {
            if (args.length == 1) {
                for (String action : List.of("list", "remove")) {
                    if (action.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) suggestions.add(action);
                }
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(java.util.Locale.ROOT).startsWith(args[0].toLowerCase(java.util.Locale.ROOT))
                            && (!(sender instanceof Player actor) || (staffLevel(online.getUniqueId()) < 0
                            || staffLevel(actor.getUniqueId()) < staffLevel(online.getUniqueId())))) {
                        suggestions.add(online.getName());
                    }
                }
            } else if (args.length == 2 && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("remove")) {
                for (String duration : List.of("30m", "1h", "2h", "6h", "1d", "3d", "7d", "30d", "1w", "perm")) {
                    if (duration.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) suggestions.add(duration);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                for (org.bukkit.BanEntry<String> entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
                    if (entry.getTarget().toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                        suggestions.add(entry.getTarget());
                }
            }
            return suggestions;
        }
        if (Set.of("timeout", "untimeout").contains(command.getName().toLowerCase(java.util.Locale.ROOT))
                && sender instanceof Player staff && hasStaffRank(staff) && args.length == 1) {
            int actorLevel = staffLevel(staff.getUniqueId());
            for (Player online : Bukkit.getOnlinePlayers()) {
                int targetLevel = staffLevel(online.getUniqueId());
                boolean canTarget = targetLevel < 0 || (actorLevel >= 0 && actorLevel < targetLevel);
                if (canTarget && online.getName().toLowerCase(java.util.Locale.ROOT)
                        .startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) suggestions.add(online.getName());
            }
            return suggestions;
        }
        if (command.getName().equalsIgnoreCase("timeout") && args.length == 2) {
            for (String duration : List.of("30s", "5m", "10m", "30m", "1h", "2h", "6h", "1d", "3d", "7d", "30d"))
                if (duration.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) suggestions.add(duration);
            return suggestions;
        }

        if (command.getName().equalsIgnoreCase("rank")) {
            if (args.length == 1) {
                List<String> actions = sender instanceof Player p && hasStaffRank(p)
                    ? List.of("give", "remove", "choose") : List.of("choose");
                for (String action : actions) if (action.startsWith(args[0].toLowerCase())) suggestions.add(action);
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove"))
                    && sender instanceof Player p && hasStaffRank(p)) {
                for (Player online : Bukkit.getOnlinePlayers()) if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) suggestions.add(online.getName());
            } else if ((args.length == 2 && args[0].equalsIgnoreCase("choose"))
                    || (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove")))) {
                List<String> ranks = args[0].equalsIgnoreCase("choose") ? PLAYER_RANKS
                    : java.util.stream.Stream.concat(PLAYER_RANKS.stream(), STAFF_RANKS.stream()).toList();
                for (String rank : ranks) if (rank.toLowerCase().startsWith(args[args.length-1].toLowerCase())) suggestions.add(rank);
            }
            return suggestions;
        }
        if (command.getName().equalsIgnoreCase("announcement")) {
            if (sender.isOp() && args.length == 1 && "send".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                suggestions.add("send");
            }
            return suggestions;
        }
        if (command.getName().equalsIgnoreCase("combatfriend")) {
            if (args.length == 1) {
                for (String action : List.of("add", "accept", "deny", "remove", "list")) {
                    if (action.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) suggestions.add(action);
                }
            } else if (args.length == 2) {
                String action = args[0].toLowerCase(java.util.Locale.ROOT);
                if (action.equals("add")) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online != sender && online.getName().toLowerCase().startsWith(args[1].toLowerCase())) suggestions.add(online.getName());
                    }
                } else if ((action.equals("accept") || action.equals("deny")) && sender instanceof Player player) {
                    for (UUID id : pendingCombatFriendRequests.getOrDefault(player.getUniqueId(), Set.of())) {
                        String name = Bukkit.getOfflinePlayer(id).getName();
                        if (name != null && name.toLowerCase().startsWith(args[1].toLowerCase())) suggestions.add(name);
                    }
                } else if (action.equals("remove") && sender instanceof Player player) {
                    for (UUID id : combatFriends.getOrDefault(player.getUniqueId(), Set.of())) {
                        String name = Bukkit.getOfflinePlayer(id).getName();
                        if (name != null && name.toLowerCase().startsWith(args[1].toLowerCase())) suggestions.add(name);
                    }
                }
            }
            return suggestions;
        }
        if (command.getName().equalsIgnoreCase("playertag")) {
            if (!sender.isOp()) return suggestions;
            if (args.length == 1) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) suggestions.add(player.getName());
                }
            } else if (args.length == 2) {
                for (String tag : List.of("[OWNER]", "[ADMIN]", "[MOD]", "[HELPER]", "[VIP]", "[MEMBER]")) {
                    if (tag.toLowerCase().startsWith(args[1].toLowerCase())) suggestions.add(tag);
                }
            } else if (args.length >= 3 && args.length <= 5) {
                String color = args.length == 3 ? "#FFFF00" : args.length == 4 ? "#FFEA00" : "#FFD700";
                if (color.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) suggestions.add(color);
            }
        } else if (command.getName().equalsIgnoreCase("protection")) {
            if (args.length == 1) {
                List<String> options = sender.isOp() ? List.of("give", "remove", "clear") : List.of("clear");
                for (String option : options) {
                    if (option.startsWith(args[0].toLowerCase())) suggestions.add(option);
                }
            } else if (sender.isOp() && args.length == 2 && List.of("give", "remove").contains(args[0].toLowerCase())) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) suggestions.add(player.getName());
                }
            }
        } else if ((command.getName().equalsIgnoreCase("invsee") || command.getName().equalsIgnoreCase("endersee")) && args.length == 1) {
            if (sender.isOp()) for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) suggestions.add(player.getName());
            }
        }
        return suggestions;
    }

    private java.time.Duration parseBanDuration(String input) {
        if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) return java.time.Duration.ZERO;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^([0-9]+)([smhdw])$").matcher(input);
        if (!m.matches()) return null;
        try {
            long n = Long.parseLong(m.group(1));
            if (n < 1 || n > 36500) return null;
            return switch (m.group(2).toLowerCase()) {
                case "s" -> java.time.Duration.ofSeconds(n);
                case "m" -> java.time.Duration.ofMinutes(n);
                case "h" -> java.time.Duration.ofHours(n);
                case "d" -> java.time.Duration.ofDays(n);
                case "w" -> java.time.Duration.ofDays(Math.multiplyExact(n, 7));
                default -> null;
            };
        } catch (ArithmeticException | NumberFormatException ex) { return null; }
    }
    private void applyIgnColor(Player player) {
        String raw = rawIgnColors.get(player.getUniqueId());
        Component ign = raw == null
            ? Component.text(player.getName())
            : mini.deserialize("<bold>" + raw + "</bold>");
        player.displayName(ign);
        Component tag = tags.get(player.getUniqueId());
        player.playerListName(tag == null ? ign : tag.append(Component.space()).append(ign));
    }
    private String color(String value) {
        return switch (value.toLowerCase()) {
            case "green" -> "#00FF00";
            case "white" -> "#FFFFFF";
            case "blue" -> "#0000FF";
            case "red" -> "#FF0000";
            case "purple" -> "#8000FF";
            case "yellow" -> "#FFFF00";
            case "aqua" -> "#00FFFF";
            case "pink" -> "#FF69B4";
            case "orange" -> "#FF8C00";
            case "black" -> "#000000";
            case "gray", "grey" -> "#808080";
            default -> null;
        };
    }

    private void applyTagToOnlinePlayer(UUID id, String raw) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        Component prefix = mini.deserialize(raw + " ");
        tags.put(id, mini.deserialize(raw));
        applyIgnColor(player);
        // Tab-list name decoration is scoreboard-team based. Some plugins give
        // each player a private scoreboard, so update the main board AND every
        // online player's active scoreboard instead of only the main board.
        String teamName = "ask_" + id.toString().replace("-", "").substring(0, 12);
        java.util.Set<Scoreboard> boards = new java.util.HashSet<>();
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player viewer : Bukkit.getOnlinePlayers()) boards.add(viewer.getScoreboard());
        for (Scoreboard board : boards) {
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            team.prefix(prefix);
            team.addEntry(player.getName());
        }
    }
}