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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.event.player.PlayerMoveEvent;
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

public final class AskyriaEssentials extends JavaPlugin implements Listener, TabCompleter {
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Component> tags = new HashMap<>();
    private final Map<UUID, String> rawTags = new HashMap<>();
    private final Map<UUID, String> rawIgnColors = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> spawnTeleportTasks = new HashMap<>();
    private final Map<UUID, Long> spawnCooldowns = new HashMap<>();
    private final Map<UUID, Long> rankAbilityCooldowns = new HashMap<>();
    private final Map<UUID, Scoreboard> cooldownOriginalBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> cooldownBoards = new HashMap<>();
    private final Map<UUID, Set<String>> cooldownSidebarEntries = new HashMap<>();
    private static final String DEATH_RESTORE_PATH = "death-restores.";
    private BukkitTask combatTicker;
    private BukkitTask tabTicker;
    private BukkitTask simulatedPlayersTicker;
    private SuperTotemManager superTotemManager;
    private ShopVillagerManager shopVillagerManager;
    private ShopNpcManager shopNpcManager;
    private boolean simulatedPlayersEnabled = false;
    private final List<String> simulatedPlayerNames = new ArrayList<>();
    private final Map<UUID, Set<UUID>> sentFakeEntries = new HashMap<>();
    private final Map<String, List<TextureProperty>> simulatedPlayerSkins = new HashMap<>();
    private volatile List<List<TextureProperty>> randomSkinPool = List.of();
    private PacketListenerAbstract fakePlayerPacketListener;
    private volatile List<String> statusPlayerNames = List.of();
    private volatile int statusOnlineCount = 0;
    // Distinct creator skins give the four shop NPCs visibly different looks.
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
        Bukkit.getPluginManager().registerEvents(this, this);
        shopVillagerManager = new ShopVillagerManager(this);
        Bukkit.getPluginManager().registerEvents(shopVillagerManager, this);
        getCommand("shopvillager").setExecutor(this);
        getCommand("shopnpc").setExecutor(this);
        shopNpcManager = new ShopNpcManager(this, shopVillagerManager);
        PacketEvents.getAPI().getEventManager().registerListener(shopNpcManager);
        Bukkit.getPluginManager().registerEvents(shopNpcManager, this);
        shopNpcManager.load();
        Bukkit.getPluginManager().registerEvents(new LegacyEnderChestMigration(this), this);
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
        superTotemManager = new SuperTotemManager(this);
        Bukkit.getPluginManager().registerEvents(superTotemManager, this);
        getCommand("supertotem").setExecutor(superTotemManager);
        getCommand("deathrestore").setExecutor(this);
        getCommand("deathrestore").setTabCompleter(this);
        combatTicker = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long nowTick = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateAbilityCooldownSidebar(player);
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
        if (shopNpcManager != null) PacketEvents.getAPI().getEventManager().unregisterListener(shopNpcManager);
        if (fakePlayerPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(fakePlayerPacketListener);
        }
        saveProtectionData();
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

    private void openKitsMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, Component.text("Askyria OP Kits"));
        menu.setItem(10, kitIcon(Material.NETHERITE_SWORD, "OP Sword Kit", "Sharpness V • Knockback I"));
        menu.setItem(12, kitIcon(Material.NETHERITE_CHESTPLATE, "Tank Kit", "Full Protection IV + Unbreaking III"));
        menu.setItem(14, kitIcon(Material.BOW, "OP Archer Kit", "Power V • Flame • Punch II"));
        menu.setItem(16, kitIcon(Material.END_CRYSTAL, "Crystal PvP Kit", "Obsidian • Crystals • Totems"));
        menu.setItem(29, kitIcon(Material.MACE, "Mace Kit", "Density V • Wind Burst II"));
        menu.setItem(31, kitIcon(Material.TRIDENT, "Spear Kit", "Trident combat loadout"));
        menu.setItem(33, kitIcon(Material.GOLDEN_APPLE, "UHC Kit", "UHC healing • Bow • Diamond gear"));
        player.openInventory(menu);
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

    private void addCommonPvPSupplies(List<ItemStack> items) {
        // Every kit gets standard golden apples, food, pearls, and core PvP potions.
        items.add(new ItemStack(Material.GOLDEN_APPLE, 16));
        items.add(new ItemStack(Material.COOKED_BEEF, 32));
        items.add(new ItemStack(Material.ENDER_PEARL, 16));
        items.add(potion(PotionType.STRENGTH));
        items.add(potion(PotionType.STRENGTH));
        items.add(potion(PotionType.SWIFTNESS));
        items.add(potion(PotionType.SWIFTNESS));
        items.add(potion(PotionType.FIRE_RESISTANCE));
        items.add(potion(PotionType.FIRE_RESISTANCE));
        items.add(potion(PotionType.STRONG_HEALING));
        items.add(potion(PotionType.STRONG_HEALING));
    }

    private ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }

    private ItemStack armor(Material material) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return item;
    }

    private void addNetheriteArmor(List<ItemStack> items) {
        items.add(armor(Material.NETHERITE_HELMET));
        items.add(armor(Material.NETHERITE_CHESTPLATE));
        items.add(armor(Material.NETHERITE_LEGGINGS));
        items.add(armor(Material.NETHERITE_BOOTS));
    }

    private void addNetheriteSword(List<ItemStack> items) {
        ItemStack sword = enchanted(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5);
        sword.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        sword.addUnsafeEnchantment(Enchantment.MENDING, 1);
        items.add(sword);
    }

    @EventHandler
    public void onKitsMenuClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(Component.text("Askyria OP Kits"))) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.isOp()) {
            player.closeInventory();
            player.sendMessage(Component.text("Only operators can use /kits."));
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        List<ItemStack> items = new ArrayList<>();
        switch (clicked.getType()) {
            case NETHERITE_SWORD -> {
                addNetheriteArmor(items);
                ItemStack sword = enchanted(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5);
                sword.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
                sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
                sword.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
                sword.addUnsafeEnchantment(Enchantment.MENDING, 1);
                items.add(sword);
                items.add(enchanted(Material.NETHERITE_AXE, Enchantment.SHARPNESS, 5));
                items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 2));
                items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
            }
            case NETHERITE_CHESTPLATE -> {
                addNetheriteArmor(items);
                addNetheriteSword(items);
                items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 4));
            }
            case BOW -> {
                addNetheriteArmor(items);
                addNetheriteSword(items);
                ItemStack bow = enchanted(Material.BOW, Enchantment.POWER, 5);
                bow.addUnsafeEnchantment(Enchantment.FLAME, 1);
                bow.addUnsafeEnchantment(Enchantment.PUNCH, 2);
                bow.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
                items.add(bow);
                items.add(enchanted(Material.CROSSBOW, Enchantment.QUICK_CHARGE, 3));
                items.add(enchanted(Material.CROSSBOW, Enchantment.MULTISHOT, 1));
                items.add(new ItemStack(Material.ARROW, 64));
                items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 2));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
            }
            case END_CRYSTAL -> {
                addNetheriteArmor(items);
                addNetheriteSword(items);
                items.add(new ItemStack(Material.OBSIDIAN, 64));
                items.add(new ItemStack(Material.END_CRYSTAL, 64));
                items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 4));
                items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
            }
            case MACE -> {
                addNetheriteArmor(items);
                addNetheriteSword(items);
                ItemStack mace = enchanted(Material.MACE, Enchantment.DENSITY, 5);
                mace.addUnsafeEnchantment(Enchantment.WIND_BURST, 2);
                mace.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
                mace.addUnsafeEnchantment(Enchantment.MENDING, 1);
                items.add(mace);
                items.add(enchanted(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5));
                items.add(new ItemStack(Material.WIND_CHARGE, 32));
                items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 4));
                items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
            }
            case TRIDENT -> {
                addNetheriteArmor(items);
                addNetheriteSword(items);
                ItemStack trident = enchanted(Material.TRIDENT, Enchantment.IMPALING, 5);
                trident.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
                trident.addUnsafeEnchantment(Enchantment.MENDING, 1);
                items.add(trident);
                items.add(enchanted(Material.TRIDENT, Enchantment.LOYALTY, 3));
                items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 2));
                items.add(new ItemStack(Material.GOLDEN_APPLE, 32));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
            }
            case GOLDEN_APPLE -> {
                items.add(armor(Material.DIAMOND_HELMET));
                items.add(armor(Material.DIAMOND_CHESTPLATE));
                items.add(armor(Material.DIAMOND_LEGGINGS));
                items.add(armor(Material.DIAMOND_BOOTS));
                items.add(enchanted(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3));
                items.add(enchanted(Material.BOW, Enchantment.POWER, 3));
                items.add(new ItemStack(Material.ARROW, 32));
                items.add(new ItemStack(Material.COOKED_BEEF, 32));
                items.add(new ItemStack(Material.WATER_BUCKET));
                items.add(new ItemStack(Material.LAVA_BUCKET));
                items.add(new ItemStack(Material.OAK_PLANKS, 32));
                items.add(new ItemStack(Material.COBBLESTONE, 64));
            }
            default -> { return; }
        }
        addCommonPvPSupplies(items);
        int gapCount = switch (clicked.getType()) {
            case NETHERITE_CHESTPLATE, END_CRYSTAL -> 96;
            case GOLDEN_APPLE -> 30;
            default -> 64;
        };
        items.add(new ItemStack(Material.GOLDEN_APPLE, gapCount));
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.closeInventory();
        player.sendMessage(Component.text("OP kit received."));
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
    public void onSpawnTeleportMove(PlayerMoveEvent event) {
        if (!spawnTeleportTasks.containsKey(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getWorld() != to.getWorld()
                || from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ())) {
            cancelSpawnTeleport(event.getPlayer(), "Spawn teleport cancelled because you moved.");
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

            // One-time CORE SMP starter kit for first-time players.
            player.getInventory().setArmorContents(new ItemStack[] {
                new ItemStack(Material.IRON_BOOTS),
                new ItemStack(Material.IRON_LEGGINGS),
                new ItemStack(Material.IRON_CHESTPLATE),
                new ItemStack(Material.IRON_HELMET)
            });
            List<ItemStack> starterItems = List.of(
                new ItemStack(Material.STONE_SWORD),
                new ItemStack(Material.STONE_PICKAXE),
                new ItemStack(Material.STONE_AXE),
                new ItemStack(Material.STONE_SHOVEL),
                new ItemStack(Material.COOKED_BEEF, 32),
                new ItemStack(Material.BREAD, 16),
                new ItemStack(Material.TORCH, 16),
                new ItemStack(Material.SHIELD)
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
        String raw = rawTags.get(id);
        if (raw != null) applyTagToOnlinePlayer(id, raw);
        applyIgnColor(player);
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
                if (shopNpcManager != null) shopNpcManager.refreshSkins();
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

    public List<TextureProperty> getShopNpcSkin(String type) {
        if (randomSkinPool.isEmpty()) return List.of();
        int index = switch (type) {
            case "armor" -> 0;
            case "kits" -> 1;
            case "mythical" -> 2;
            default -> 3;
        };
        return randomSkinPool.get(index % randomSkinPool.size());
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
                "<white>ONLINE PLAYERS:</white> <light_purple>" + statusOnlineCount + "</light_purple>" +
                " <gray>|</gray> <white>PING:</white> <red>" + Math.max(0, player.getPing()) + "ms</red>"
            );
            Component footer = mini.deserialize(
                "<#FF8C00><bold>/discord</bold></#FF8C00>"
            );
            player.sendPlayerListHeaderAndFooter(header, footer);
            sendFakeEntries(player, fakeNames);
        }
    }

    private int topRank(Player player) {
        String raw = rawTags.get(player.getUniqueId());
        if (raw == null) return 0;
        String normalized = raw.toUpperCase().replaceAll("<[^>]*>", "");
        for (int rank = 1; rank <= 5; rank++) {
            if (normalized.contains("#" + rank) || normalized.contains("TOP " + rank) || normalized.contains("TOP" + rank)) return rank;
        }
        return 0;
    }

    private void updateAbilityCooldownSidebar(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long rankRemaining = Math.max(0L, (rankAbilityCooldowns.getOrDefault(id, 0L) - now + 999L) / 1000L);
        boolean showRank = topRank(player) > 0 && rankRemaining > 0L;
        long switcherRemaining = shopVillagerManager == null ? 0L : shopVillagerManager.getSwitcherCooldownSeconds(player);
        boolean showSwitcher = switcherRemaining > 0L;

        Scoreboard board = cooldownBoards.get(id);
        if (!showRank && !showSwitcher) {
            if (board != null) {
                Objective objective = board.getObjective("ask_cd");
                if (objective != null) objective.unregister();
                Scoreboard original = cooldownOriginalBoards.remove(id);
                cooldownBoards.remove(id);
                cooldownSidebarEntries.remove(id);
                if (original != null && player.getScoreboard() == board) player.setScoreboard(original);
            }
            return;
        }

        if (board == null) {
            Scoreboard original = player.getScoreboard();
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            for (Team source : original.getTeams()) {
                Team copy = board.registerNewTeam(source.getName());
                copy.prefix(source.prefix());
                copy.suffix(source.suffix());
                for (String entry : source.getEntries()) copy.addEntry(entry);
            }
            cooldownOriginalBoards.put(id, original);
            cooldownBoards.put(id, board);
            player.setScoreboard(board);
        }

        Objective objective = board.getObjective("ask_cd");
        if (objective == null) {
            objective = board.registerNewObjective("ask_cd", org.bukkit.scoreboard.Criteria.DUMMY, Component.text("COOLDOWNS", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        Set<String> previousEntries = cooldownSidebarEntries.computeIfAbsent(id, ignored -> new HashSet<>());
        for (String entry : previousEntries) board.resetScores(entry);
        previousEntries.clear();
        if (showRank) {
            String entry = ChatColor.YELLOW + "TOP " + topRank(player) + ChatColor.WHITE + " " + rankRemaining + "s";
            objective.getScore(entry).setScore(2);
            previousEntries.add(entry);
        }
        if (showSwitcher) {
            String entry = ChatColor.AQUA + "SWITCHER " + ChatColor.WHITE
                + (switcherRemaining / 60) + "m " + (switcherRemaining % 60) + "s";
            objective.getScore(entry).setScore(1);
            previousEntries.add(entry);
        }
    }

    private String rankTag(int rank) {
        return switch (rank) {
            case 1 -> "<#FFFF00><bold>[#1]</bold></#FFFF00>";
            case 2 -> "<#808080><bold>[#2]</bold></#808080>";
            case 3 -> "<#CD7F32><bold>[#3]</bold></#CD7F32>";
            case 4 -> "<white><bold>[#4]</bold></white>";
            case 5 -> "<white><bold>[#5]</bold></white>";
            default -> "";
        };
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
        String teamName = "ask_" + id.toString().replace("-", "").substring(0, 12);
        java.util.Set<Scoreboard> boards = new java.util.HashSet<>();
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player viewer : Bukkit.getOnlinePlayers()) boards.add(viewer.getScoreboard());
        for (Scoreboard board : boards) {
            Team team = board.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) team.unregister();
            }
        }
        saveConfig();
        updateTabLists();
    }


    @EventHandler
    public void saveDeathInventory(PlayerDeathEvent event) {
        // Let Bukkit/Paper handle normal death drops. Do not copy the inventory
        // into the restore cache or clear the event drops.
        String path = DEATH_RESTORE_PATH + event.getEntity().getUniqueId();
        getConfig().set(path, null);
        saveConfig();
    }

    private boolean restoreDeathInventory(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online to receive their restored items."));
            return true;
        }
        String path = DEATH_RESTORE_PATH + target.getUniqueId();
        List<?> saved = getConfig().getList(path);
        if (saved == null || saved.isEmpty()) {
            sender.sendMessage(Component.text("No saved death loot found for " + target.getName() + "."));
            return true;
        }
        for (Object value : saved) {
            if (value instanceof ItemStack item && !item.getType().isAir()) {
                Map<Integer, ItemStack> overflow = target.getInventory().addItem(item.clone());
                overflow.values().forEach(leftover ->
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            }
        }
        getConfig().set(path, null);
        saveConfig();
        sender.sendMessage(Component.text("Restored " + target.getName() + "'s most recent death loot."));
        target.sendMessage(Component.text("Your most recent death loot has been restored."));
        return true;
    }

    @EventHandler
    public void onTopRankDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        int victimRank = topRank(victim);
        int killerRank = topRank(killer);
        if (victimRank == 0 || killerRank == 0) return;

        // Higher rank number means lower standing: only an upset by the lower-ranked
        // player swaps the two ranks. Higher-ranked players killing lower-ranked
        // players does not change either rank.
        if (killerRank < victimRank) return;

        setStoredTag(killer, rankTag(victimRank));
        setStoredTag(victim, rankTag(killerRank));
        Bukkit.broadcast(Component.text(killer.getName() + " defeated #" + victimRank
            + " and swapped ranks with " + victim.getName() + "!"));
    }

    @EventHandler
    public void onTopRankCrouchJump(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        int rank = topRank(player);
        if (rank == 0 || !player.isSneaking() || player.isOnGround()) return;
        if (event.getTo() == null || event.getTo().getY() <= event.getFrom().getY()) return;
        long now = System.currentTimeMillis();
        if (now < rankAbilityCooldowns.getOrDefault(player.getUniqueId(), 0L)) return;
        switch (rank) {
            case 1 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 25 * 20, 1, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 10 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 25 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, true, true, true));
            }
            case 2 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 22 * 20, 1, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 10 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 18 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, true, true, true));
            }
            case 3 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 18 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 8 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 25 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 18 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, true, true, true));
            }
            case 4 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 22 * 20, 1, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 22 * 20, 1, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, true, true, true));
            }
            case 5 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 18 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 25 * 20, 1, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, true, true, true));
            }
            default -> { return; }
        }
        long cooldown = rank == 3 || rank == 5 ? 75_000L : 60_000L;
        rankAbilityCooldowns.put(player.getUniqueId(), now + cooldown);
        player.sendMessage(Component.text("#" + rank + " ability activated!"));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
            player.getLocation().add(0, 1, 0), 24, 0.45, 0.6, 0.45, 0.05);
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

    private boolean isInSpawnProtection(Location location) {
        if (location == null || location.getWorld() == null) return false;
        int radius = Math.max(0, getConfig().getInt("spawn-protection-radius", 100));
        Location spawn = location.getWorld().getSpawnLocation();
        if (!location.getWorld().equals(spawn.getWorld())) return false;
        double dx = location.getX() - spawn.getX();
        double dz = location.getZ() - spawn.getZ();
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    private void denySpawnAction(Player player) {
        player.sendActionBar(Component.text("SPAWN IS PROTECTED"));
    }

    @EventHandler
    public void onSpawnBlockBreak(BlockBreakEvent event) {
        if (!event.getPlayer().isOp() && isInSpawnProtection(event.getBlock().getLocation())) {
            event.setCancelled(true);
            denySpawnAction(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpawnBlockPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().isOp() && isInSpawnProtection(event.getBlock().getLocation())) {
            event.setCancelled(true);
            denySpawnAction(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpawnBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!event.getPlayer().isOp() && isInSpawnProtection(event.getBlock().getRelative(event.getBlockFace()).getLocation())) {
            event.setCancelled(true);
            denySpawnAction(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpawnBucketFill(PlayerBucketFillEvent event) {
        if (!event.getPlayer().isOp() && isInSpawnProtection(event.getBlock().getLocation())) {
            event.setCancelled(true);
            denySpawnAction(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpawnEntityExplosion(EntityExplodeEvent event) {
        if (isInSpawnProtection(event.getLocation())) event.blockList().clear();
    }

    @EventHandler
    public void onSpawnBlockExplosion(BlockExplodeEvent event) {
        if (isInSpawnProtection(event.getBlock().getLocation())) event.blockList().clear();
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
            if (!isProtected(victim)) {
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
            case "kits" -> {
                if (!requireOp(sender)) return true;
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can open the kits menu."));
                    return true;
                }
                openKitsMenu(player);
                return true;
            }
            case "shopvillager" -> {
                if (!requireOp(sender)) return true;
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can manage shop villagers."));
                    return true;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
                    shopVillagerManager.spawnShopVillager(player, args[1]);
                } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
                    shopVillagerManager.openEditor(player, args[1].toLowerCase(java.util.Locale.ROOT));
                } else if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
                    shopVillagerManager.removeTargeted(player);
                } else if (args.length == 1 && args[0].equalsIgnoreCase("removeall")) {
                    shopVillagerManager.removeAll(player);
                } else {
                    sender.sendMessage(Component.text("Usage: /shopvillager spawn <armor|kits|mythical|seller>"));
                }
                return true;
            }
            case "shopnpc" -> {
                if (!requireOp(sender)) return true;
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can manage shop NPCs."));
                    return true;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
                    shopNpcManager.spawnShopNpc(player, args[1]);
                } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
                    shopVillagerManager.openEditor(player, args[1].toLowerCase(java.util.Locale.ROOT));
                } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                    shopNpcManager.removeShopNpc(player, args[1].toLowerCase(java.util.Locale.ROOT));
                } else if (args.length == 1 && args[0].equalsIgnoreCase("removeall")) {
                    shopNpcManager.removeAll(player);
                } else {
                    sender.sendMessage(Component.text("Usage: /shopnpc spawn <armor|mythical|seller>"));
                    sender.sendMessage(Component.text("/shopnpc edit <armor|mythical|seller>"));
                    sender.sendMessage(Component.text("/shopnpc remove <type> OR /shopnpc removeall"));
                }
                return true;
            }
            case "deathrestore" -> {
                if (!requireOp(sender)) return true;
                if (args.length != 1) { sender.sendMessage(Component.text("Usage: /deathrestore <player>")); return true; }
                return restoreDeathInventory(sender, args[0]);
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
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use /spawn."));
                    return true;
                }
                beginSpawnTeleport(player);
                return true;
            }
            case "protection" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use this command."); return true;
                }
                if (args.length != 1 || !args[0].equalsIgnoreCase("clear")) {
                    player.sendMessage(Component.text("Usage: /protection clear")); return true;
                }
                if (protectedUntil.remove(player.getUniqueId()) != null) {
                    saveProtectionData();
                    player.sendMessage(Component.text("Your protection has been removed. You can now take damage."));
                } else player.sendMessage(Component.text("You do not have active protection."));
                return true;
            }
            case "top1", "top2", "top3", "top4", "top5" -> {
                if (!requireOp(sender)) return true;
                if (args.length != 1) {
                    sender.sendMessage(Component.text("Usage: /" + command.getName().toLowerCase() + " <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player '" + args[0] + "' is not online."));
                    return true;
                }
                int rank = Integer.parseInt(command.getName().substring(3));
                // Keep each TOP rank unique: remove it from its current holder first.
                for (Map.Entry<UUID, String> entry : new HashMap<>(rawTags).entrySet()) {
                    String normalized = entry.getValue().toUpperCase().replaceAll("<[^>]*>", "");
                    if (normalized.contains("TOP " + rank) || normalized.contains("TOP" + rank)) {
                        Player oldHolder = Bukkit.getPlayer(entry.getKey());
                        if (oldHolder != null && !oldHolder.equals(target)) {
                            clearStoredTag(oldHolder);
                        } else if (oldHolder == null) {
                            rawTags.remove(entry.getKey());
                            tags.remove(entry.getKey());
                            getConfig().set("player-tags." + entry.getKey(), null);
                        }
                    }
                }
                setStoredTag(target, rankTag(rank));
                saveConfig();
                sender.sendMessage(Component.text("Gave " + target.getName() + " #" + rank + ". "));
                Bukkit.broadcast(Component.text(target.getName() + " is now #" + rank + "!"));
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
                rawTags.put(target.getUniqueId(), raw);
                getConfig().set("player-tags." + target.getUniqueId(), raw);
                saveConfig();
                applyTagToOnlinePlayer(target.getUniqueId(), raw);
                sender.sendMessage(Component.text("Set " + target.getName() + "'s tag to " + tagText + "."));
                return true;
            }
            case "invsee", "endersee" -> {
                if (!requireOp(sender)) return true;
                if (!(sender instanceof Player viewer)) { sender.sendMessage("Only players can use this command."); return true; }
                if (args.length != 1) { sender.sendMessage(Component.text("Usage: /" + label + " <player>")); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage(Component.text("That player must be online.")); return true; }
                if (command.getName().equalsIgnoreCase("invsee")) viewer.openInventory(target.getInventory());
                else viewer.openInventory(target.getEnderChest());
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("deathrestore") && sender.isOp() && args.length == 1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) suggestions.add(player.getName());
            }
        } else if (command.getName().equalsIgnoreCase("shopvillager") && sender.isOp()) {
            if (args.length == 1) {
                for (String option : List.of("spawn", "remove", "removeall")) {
                    if (option.startsWith(args[0].toLowerCase())) suggestions.add(option);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
                for (String type : List.of("armor", "mythical", "seller")) {
                    if (type.startsWith(args[1].toLowerCase())) suggestions.add(type);
                }
            }
        } else if (command.getName().equalsIgnoreCase("shopnpc") && sender.isOp()) {
            if (args.length == 1) {
                for (String option : List.of("spawn", "edit", "remove", "removeall")) {
                    if (option.startsWith(args[0].toLowerCase())) suggestions.add(option);
                }
            } else if (args.length == 2 && List.of("spawn", "edit", "remove").contains(args[0].toLowerCase())) {
                for (String type : List.of("armor", "mythical", "seller")) {
                    if (type.startsWith(args[1].toLowerCase())) suggestions.add(type);
                }
            }
        } else if (command.getName().equalsIgnoreCase("playertag")) {
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
        } else if (command.getName().equalsIgnoreCase("protection") && args.length == 1 && sender instanceof Player) {
            if ("clear".startsWith(args[0].toLowerCase())) suggestions.add("clear");
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
            ? Component.text(player.getName()).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
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