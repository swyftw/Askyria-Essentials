package com.askyria.essentials;

import io.papermc.paper.event.player.AsyncChatEvent;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AskyriaEssentials extends JavaPlugin implements Listener, TabCompleter {
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Component> tags = new HashMap<>();
    private final Map<UUID, String> rawTags = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private final Map<UUID, Long> rankAbilityCooldowns = new HashMap<>();
    private BukkitTask combatTicker;
    private BukkitTask tabTicker;
    private final MiniMessage mini = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
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
        Bukkit.getPluginManager().registerEvents(this, this);
        OrbitalManager orbitalManager = new OrbitalManager(this);
        Bukkit.getPluginManager().registerEvents(orbitalManager, this);
        getCommand("orbital").setExecutor(orbitalManager);
        combatTicker = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long nowTick = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isProtected(player)) {
                    Long protectionExpiry = protectedUntil.get(player.getUniqueId());
                    long seconds = protectionExpiry == null ? 0L
                        : Math.max(0L, (protectionExpiry - nowTick + 999L) / 1000L);
                    long minutes = seconds / 60L;
                    long remainder = seconds % 60L;
                    player.sendActionBar(Component.text("PROTECTED " + minutes + "m " + remainder + "s"));
                    continue;
                }
                Long until = combatUntil.get(player.getUniqueId());
                if (until == null) continue;
                long seconds = Math.max(0L, (until - nowTick + 999L) / 1000L);
                if (seconds <= 0) {
                    combatUntil.remove(player.getUniqueId());
                    player.sendActionBar(Component.text("You are no longer in combat."));
                } else {
                    player.sendActionBar(Component.text("COMBAT " + seconds + "s"));
                }
            }
        }, 0L, 5L);
        tabTicker = Bukkit.getScheduler().runTaskTimer(this, this::updateTabLists, 20L, 100L);
        getLogger().info("Askyria Essentials enabled.");
    }

    @Override
    public void onDisable() {
        if (combatTicker != null) combatTicker.cancel();
        if (tabTicker != null) tabTicker.cancel();
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
            player.sendMessage(Component.text("Your protection has expired! You can now take damage."));
            saveProtectionData();
            return false;
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!getConfig().getBoolean("seen-player." + id, false)) {
            long expiry = System.currentTimeMillis() + 300_000L;
            protectedUntil.put(id, expiry);
            getConfig().set("seen-player." + id, true);
            getConfig().set("protected-players." + id, expiry);
            player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 20));
            player.sendMessage(Component.text("Welcome! You have 5 minutes of full protection. Use /protection clear to remove it early."));
            saveConfig();
        } else if (isProtected(player)) {
            player.sendMessage(Component.text("Your new-player protection is still active."));
        }
        String raw = rawTags.get(id);
        if (raw != null) applyTagToOnlinePlayer(id, raw);
        updateTabLists();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, this::updateTabLists);
        Long until = combatUntil.remove(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            player.setHealth(0.0);
            Bukkit.broadcast(Component.text(player.getName() + " logged out during combat and was slain."));
        }
    }

    private void updateTabLists() {
        int count = Bukkit.getOnlinePlayers().size();
        Component header = mini.deserialize("<gradient:#39D5FF:#7B61FF><bold>ASKYRIA</bold></gradient>\n<gray>Survive. Fight. Rise.</gray>");
        Component footer = mini.deserialize("<gray>Players: </gray><white>" + count + "</white><gray>/</gray><white>" + Bukkit.getMaxPlayers() + "</white>\n<aqua>IP: </aqua><white>askyria.smp.quest</white>");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    private int topRank(Player player) {
        String raw = rawTags.get(player.getUniqueId());
        if (raw == null) return 0;
        String normalized = raw.toUpperCase().replaceAll("<[^>]*>", "");
        for (int rank = 1; rank <= 5; rank++) {
            if (normalized.contains("TOP " + rank) || normalized.contains("TOP" + rank)) return rank;
        }
        return 0;
    }

    private String rankTag(int rank) {
        return switch (rank) {
            case 1 -> "<#FFFF00><bold>[TOP1]</bold></#FFFF00>";
            case 2 -> "<#808080><bold>[TOP2]</bold></#808080>";
            case 3 -> "<#CD7F32><bold>[TOP3]</bold></#CD7F32>";
            case 4 -> "<white><bold>[TOP4]</bold></white>";
            case 5 -> "<white><bold>[TOP5]</bold></white>";
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
    public void onTopRankDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        int rank = topRank(victim);
        if (rank == 0 || killer == null || killer.equals(victim)) return;
        setStoredTag(killer, rankTag(rank));
        // Remove the defeated holder's tag entirely; don't assign a MEMBER tag.
        rawTags.remove(victim.getUniqueId());
        tags.remove(victim.getUniqueId());
        getConfig().set("player-tags." + victim.getUniqueId(), null);
        applyTagToOnlinePlayer(victim.getUniqueId(), "");
        saveConfig();
        updateTabLists();
        Bukkit.broadcast(Component.text(killer.getName() + " claimed TOP " + rank + " by defeating " + victim.getName() + "!"));
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
        player.sendMessage(Component.text("TOP " + rank + " ability activated!"));
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

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
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
    public void onPortal(PlayerPortalEvent event) {
        if (getConfig().getBoolean("end-closed", false)) {
            Location to = event.getTo();
            if (to != null && to.getWorld() != null && to.getWorld().getEnvironment() == World.Environment.THE_END) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("The End is currently closed."));
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Component tag = tags.get(event.getPlayer().getUniqueId());
        if (tag != null) {
            event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.empty().append(tag).append(Component.space()).append(sourceDisplayName)
                    .append(Component.text(": ")).append(message));
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
            case "discord" -> {
                sender.sendMessage(mini.deserialize("<gold><bold>Askyria SMP Discord:</bold></gold> <aqua><underlined>https://discord.gg/qh48uacM8</underlined></aqua>"));
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
                sender.sendMessage(Component.text("Gave " + target.getName() + " TOP" + rank + ". "));
                Bukkit.broadcast(Component.text(target.getName() + " is now TOP " + rank + "!"));
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
            case "endclosed" -> {
                if (!requireOp(sender)) return true;
                getConfig().set("end-closed", true); saveConfig();
                sender.sendMessage(Component.text("The End is now closed.")); return true;
            }
            case "endopen" -> {
                if (!requireOp(sender)) return true;
                getConfig().set("end-closed", false); saveConfig();
                sender.sendMessage(Component.text("The End is now open.")); return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
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
        } else if (command.getName().equalsIgnoreCase("protection") && args.length == 1 && sender instanceof Player) {
            if ("clear".startsWith(args[0].toLowerCase())) suggestions.add("clear");
        } else if ((command.getName().equalsIgnoreCase("invsee") || command.getName().equalsIgnoreCase("endersee")) && args.length == 1) {
            if (sender.isOp()) for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) suggestions.add(player.getName());
            }
        }
        return suggestions;
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