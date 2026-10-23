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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AskyriaEssentials extends JavaPlugin implements Listener {
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Component> tags = new HashMap<>();
    private final Map<UUID, String> rawTags = new HashMap<>();
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
        getLogger().info("Askyria Essentials enabled.");
    }

    @Override
    public void onDisable() {
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
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
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

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && isProtected(victim)) {
            event.setCancelled(true);
            return;
        }
        Player attacker = attackerFrom(event.getDamager());
        if (attacker != null && isProtected(attacker) && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            attacker.sendMessage(Component.text("You cannot attack players while your protection is active."));
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
            case "playertag" -> {
                if (!requireOp(sender)) return true;
                if (args.length != 5) {
                    sender.sendMessage(Component.text("Usage: /playertag <player> <tag> <fade1> <fade2> <fade3>")); return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage(Component.text("That player must be online.")); return true; }
                String tagText = args[1];
                String c1 = color(args[2]), c2 = color(args[3]), c3 = color(args[4]);
                if (c1 == null || c2 == null || c3 == null) {
                    sender.sendMessage(Component.text("Use three hex colors, for example #FF0000 #FF00FF #0000FF.")); return true;
                }
                String raw = "<gradient:" + c1 + ":" + c2 + ":" + c3 + ">" + tagText + "</gradient>";
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

    private String color(String value) {
        if (!value.matches("#[0-9a-fA-F]{6}")) return null;
        return value;
    }

    private void applyTagToOnlinePlayer(UUID id, String raw) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        Component prefix = mini.deserialize(raw + " ");
        tags.put(id, mini.deserialize(raw));
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "ask_" + id.toString().replace("-", "").substring(0, 12);
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.prefix(prefix);
        team.addEntry(player.getName());
    }
}