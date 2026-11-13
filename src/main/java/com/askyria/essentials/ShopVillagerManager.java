package com.askyria.essentials;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

public final class ShopVillagerManager implements Listener {
    private final AskyriaEssentials plugin;
    private final NamespacedKey shopVillagerKey;
    private final NamespacedKey shardKey;
    private final NamespacedKey orbitalKey;
    private final NamespacedKey tradeKey;
    private final Map<String, Trade> trades = new java.util.LinkedHashMap<>();

    private record Trade(String id, Material material, int amount, int price, String display, String category) {}

    public ShopVillagerManager(AskyriaEssentials plugin) {
        this.plugin = plugin;
        this.shopVillagerKey = new NamespacedKey(plugin, "core_shop_villager");
        this.shardKey = new NamespacedKey(plugin, "core_shard");
        this.orbitalKey = new NamespacedKey(plugin, "orbital_type");
        this.tradeKey = new NamespacedKey(plugin, "core_shop_trade");
        add("armor_helmet", Material.NETHERITE_HELMET, 1, 600, "Netherite Helmet (Protection V)", "armor");
        add("armor_chest", Material.NETHERITE_CHESTPLATE, 1, 900, "Netherite Chestplate (Protection V)", "armor");
        add("armor_legs", Material.NETHERITE_LEGGINGS, 1, 750, "Netherite Leggings (Protection V)", "armor");
        add("armor_boots", Material.NETHERITE_BOOTS, 1, 600, "Netherite Boots (Protection V)", "armor");
        add("armor_set", Material.NETHERITE_CHESTPLATE, 1, 2700, "Full Netherite Set (Protection V)", "armor");

        add("normal_gap", Material.GOLDEN_APPLE, 64, 45, "Golden Apples", "normal");
        add("normal_egap", Material.ENCHANTED_GOLDEN_APPLE, 8, 200, "Enchanted Golden Apples", "normal");
        add("normal_pearl", Material.ENDER_PEARL, 64, 65, "Ender Pearls", "normal");
        add("normal_totem", Material.TOTEM_OF_UNDYING, 4, 160, "Totems of Undying", "normal");
        add("normal_rocket", Material.FIREWORK_ROCKET, 128, 35, "Firework Rockets", "normal");
        add("normal_xp", Material.EXPERIENCE_BOTTLE, 64, 20, "Experience Bottles", "normal");
        add("normal_shulker", Material.SHULKER_BOX, 4, 120, "Shulker Boxes", "normal");
        add("normal_obsidian", Material.OBSIDIAN, 64, 25, "Obsidian", "normal");
        add("normal_crystal", Material.END_CRYSTAL, 16, 80, "End Crystals", "normal");
        add("normal_anchor", Material.RESPAWN_ANCHOR, 16, 60, "Respawn Anchors", "normal");
        add("normal_glowstone", Material.GLOWSTONE, 64, 15, "Glowstone", "normal");
        add("normal_tnt", Material.TNT, 32, 45, "TNT", "normal");
        add("normal_carrot", Material.GOLDEN_CARROT, 64, 25, "Golden Carrots", "normal");
        add("normal_arrow", Material.ARROW, 128, 20, "Arrows", "normal");
        add("normal_enderchest", Material.ENDER_CHEST, 4, 80, "Ender Chests", "normal");
        add("normal_anvil", Material.ANVIL, 4, 50, "Anvils", "normal");
        add("normal_trident", Material.TRIDENT, 1, 150, "Trident", "normal");
        add("normal_mending", Material.ENCHANTED_BOOK, 1, 100, "Mending Book", "normal");

        add("sell_iron", Material.IRON_BLOCK, 16, 4, "Iron Blocks", "seller");
        add("sell_gold", Material.GOLD_BLOCK, 16, 4, "Gold Blocks", "seller");
        add("sell_diamond", Material.DIAMOND_BLOCK, 16, 16, "Diamond Blocks", "seller");
        add("sell_emerald", Material.EMERALD_BLOCK, 16, 8, "Emerald Blocks", "seller");
        add("sell_coal", Material.COAL_BLOCK, 16, 2, "Coal Blocks", "seller");
        add("sell_redstone", Material.REDSTONE_BLOCK, 16, 3, "Redstone Blocks", "seller");
        add("sell_lapis", Material.LAPIS_BLOCK, 16, 3, "Lapis Blocks", "seller");
        add("sell_netherite", Material.NETHERITE_BLOCK, 1, 40, "Netherite Blocks", "seller");
        add("sell_totem", Material.TOTEM_OF_UNDYING, 8, 40, "Totems of Undying", "seller");
        add("sell_elytra", Material.ELYTRA, 4, 40, "Elytra", "seller");
        add("sell_heavycore", Material.HEAVY_CORE, 8, 64, "Heavy Cores", "seller");
        add("sell_star", Material.NETHER_STAR, 16, 80, "Nether Stars", "seller");
        add("sell_shulker", Material.SHULKER_BOX, 16, 48, "Shulker Boxes", "seller");
        add("sell_egg", Material.DRAGON_EGG, 1, 1000, "Dragon Egg", "seller");
    }

    private void add(String id, Material material, int amount, int price, String display, String category) {
        trades.put(id, new Trade(id, material, amount, price, display, category));
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
        villager.setCanPickupItems(false);
    }

    public void spawnShopVillager(Player player, String type) {
        String normalized = type.toLowerCase(java.util.Locale.ROOT);
        String displayName;
        Villager.Profession profession;
        switch (normalized) {
            case "armor" -> { displayName = "Armor Villager"; profession = Villager.Profession.ARMORER; }
            case "normal" -> { displayName = "Normal Items Villager"; profession = Villager.Profession.TOOLSMITH; }
            case "mythical" -> { displayName = "Mythical Weapons Villager"; profession = Villager.Profession.WEAPONSMITH; }
            case "seller" -> { displayName = "Seller Villager"; profession = Villager.Profession.BUTCHER; }
            default -> { player.sendMessage(Component.text("Type must be armor, normal, mythical, or seller.")); return; }
        }
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        if (direction.lengthSquared() == 0) direction = new Vector(0, 0, 1);
        var spawnLocation = player.getLocation().clone().add(direction.multiply(2));
        spawnLocation.setPitch(0);
        Villager villager = player.getWorld().spawn(spawnLocation, Villager.class);
        villager.setProfession(profession);
        villager.setVillagerLevel(5);
        villager.setCustomName(displayName);
        villager.setCustomNameVisible(true);
        markShopVillager(villager);
        player.sendMessage(Component.text("Spawned " + displayName + "."));
    }

    @EventHandler
    public void onShopVillagerInteract(PlayerInteractEntityEvent event) {
        if (!isShopVillager(event.getRightClicked())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String name = event.getRightClicked().getCustomName();
        if (name == null) return;
        openShop(player, name.startsWith("Armor") ? "armor" :
            name.startsWith("Normal") ? "normal" :
            name.startsWith("Mythical") ? "mythical" : "seller");
    }

    private void openShop(Player player, String category) {
        String title = switch (category) {
            case "armor" -> "Armor Villager";
            case "normal" -> "Normal Items Villager";
            case "mythical" -> "Mythical Weapons Villager";
            default -> "Seller Villager";
        };
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));
        int slot = 0;
        if (category.equals("mythical")) {
            addOrbitalIcon(inv, 10, "rain", "RAIN ORBITAL", 100);
            addOrbitalIcon(inv, 12, "stab", "STAB ORBITAL", 125);
            addOrbitalIcon(inv, 14, "lunar", "LUNAR ORBITAL", 175);
            addOrbitalIcon(inv, 16, "nuke", "NUKE ORBITAL", 250);
        } else {
            for (Trade trade : trades.values()) {
                if (!trade.category().equals(category)) continue;
                inv.setItem(slot++, tradeIcon(trade, category.equals("seller")));
            }
        }
        player.openInventory(inv);
    }

    private ItemStack tradeIcon(Trade trade, boolean selling) {
        ItemStack icon = new ItemStack(trade.material());
        if (trade.id().equals("armor_helmet") || trade.id().equals("armor_chest")
                || trade.id().equals("armor_legs") || trade.id().equals("armor_boots")) {
            icon.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
            icon.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        }
        if (trade.id().equals("armor_set")) icon.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text((selling ? "Sell " : "") + trade.display()));
        meta.getPersistentDataContainer().set(tradeKey, PersistentDataType.STRING, trade.id());
        meta.lore(List.of(
            Component.text("Quantity: " + trade.amount()),
            Component.text(selling ? "You receive: " + trade.price() + " Core" : "Price: " + trade.price() + " Core"),
            Component.text("Click to " + (selling ? "sell" : "purchase"))
        ));
        icon.setItemMeta(meta);
        return icon;
    }

    private void addOrbitalIcon(Inventory inv, int slot, String type, String display, int price) {
        ItemStack icon = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(display));
        meta.getPersistentDataContainer().set(orbitalKey, PersistentDataType.STRING, type);
        meta.lore(List.of(Component.text("Price: " + price + " Core"), Component.text("Single-use orbital")));
        icon.setItemMeta(meta);
        inv.setItem(slot, icon);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.contains("Villager")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        String plainTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        String category = plainTitle.startsWith("Armor") ? "armor" : plainTitle.startsWith("Normal") ? "normal" :
            plainTitle.startsWith("Mythical") ? "mythical" : "seller";
        if (category.equals("mythical")) {
            String type = clicked.getItemMeta().getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
            if (type == null) return;
            int price = switch (type) { case "rain" -> 100; case "stab" -> 125; case "lunar" -> 175; default -> 250; };
            if (!takeCore(player, price)) { player.sendMessage(Component.text("You need " + price + " Core. Your balance: " + getBalance(player) + ".")); return; }
            player.getInventory().addItem(createOrbital(type)).values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            player.sendMessage(Component.text("Purchased " + type.toUpperCase() + " Orbital."));
            return;
        }
        String tradeId = clicked.getItemMeta().getPersistentDataContainer().get(tradeKey, PersistentDataType.STRING);
        Trade selected = tradeId == null ? null : trades.get(tradeId);
        if (selected != null && selected.category().equals(category)) {
            Trade trade = selected;
            if (category.equals("seller")) sellItems(player, trade);
            else buyItems(player, trade);
            return;
        }
    }

    @EventHandler
    public void onShopDrag(InventoryDragEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("Villager")) event.setCancelled(true);
    }

    private void buyItems(Player player, Trade trade) {
        if (!takeCore(player, trade.price())) {
            player.sendMessage(Component.text("You need " + trade.price() + " Core to buy that. Your balance: " + getBalance(player) + "."));
            return;
        }
        if (trade.id().equals("armor_set")) {
            give(player, enchantedArmor(Material.NETHERITE_HELMET));
            give(player, enchantedArmor(Material.NETHERITE_CHESTPLATE));
            give(player, enchantedArmor(Material.NETHERITE_LEGGINGS));
            give(player, enchantedArmor(Material.NETHERITE_BOOTS));
        } else if (trade.id().equals("normal_trident")) {
            give(player, new ItemStack(Material.TRIDENT));
        } else if (trade.id().equals("normal_mending")) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(Enchantment.MENDING, 1, true);
            book.setItemMeta(meta);
            give(player, book);
        } else {
            ItemStack item = new ItemStack(trade.material(), trade.amount());
            if (trade.category().equals("armor")) {
                item.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            }
            give(player, item);
        }
        player.sendMessage(Component.text("Purchase complete. Core balance: " + getBalance(player) + "."));
    }

    private ItemStack enchantedArmor(Material material) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return item;
    }

    private void sellItems(Player player, Trade trade) {
        int found = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == trade.material()) found += item.getAmount();
        }
        if (found < trade.amount()) {
            player.sendMessage(Component.text("You need " + trade.amount() + " " + trade.display() + "."));
            return;
        }
        int remaining = trade.amount();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != trade.material()) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
        addBalance(player, trade.price());
        player.sendMessage(Component.text("Sold " + trade.amount() + " " + trade.display() + " for " + trade.price() + " Core. Balance: " + getBalance(player) + "."));
    }

    private ItemStack createOrbital(String type) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        String name = switch (type) {
            case "rain" -> "§b§lRAIN ORBITAL";
            case "stab" -> "§c§lSTAB ORBITAL";
            case "nuke" -> "§4§lNUKE ORBITAL";
            default -> "§d§lLUNAR ORBITAL";
        };
        meta.setDisplayName(name);
        meta.setLore(List.of("§7Single-use orbital weapon", "§fRight-click to throw at your target", "§cConsumed after one launch"));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.getPersistentDataContainer().set(orbitalKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
            .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }

    public int getBalance(Player player) {
        return Math.max(0, plugin.getConfig().getInt("core-balances." + player.getUniqueId(), 0));
    }

    public void addBalance(Player player, int amount) {
        if (amount <= 0) return;
        String path = "core-balances." + player.getUniqueId();
        plugin.getConfig().set(path, Math.addExact(getBalance(player), amount));
        plugin.saveConfig();
    }

    public void setBalance(Player player, int amount) {
        plugin.getConfig().set("core-balances." + player.getUniqueId(), Math.max(0, amount));
        plugin.saveConfig();
    }

    public boolean takeCore(Player player, int amount) {
        if (amount < 0 || getBalance(player) < amount) return false;
        setBalance(player, getBalance(player) - amount);
        return true;
    }

    public void handleCoreCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
            sender.sendMessage(Component.text("Your Core balance: " + getBalance(player)));
            return;
        }
        if (!sender.isOp()) { sender.sendMessage(Component.text("Only operators can manage Core balances.")); return; }
        if (args.length != 3 || !(args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("take"))) {
            sender.sendMessage(Component.text("Usage: /core balance OR /core <give|set|take> <player> <amount>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(Component.text("That player must be online.")); return; }
        int amount;
        try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage(Component.text("Enter a whole-number amount.")); return; }
        if (amount < 0 || amount > 100000000) { sender.sendMessage(Component.text("Amount must be between 0 and 100000000.")); return; }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "give" -> addBalance(target, amount);
            case "set" -> setBalance(target, amount);
            case "take" -> setBalance(target, Math.max(0, getBalance(target) - amount));
            default -> { return; }
        }
        sender.sendMessage(Component.text("Updated " + target.getName() + "'s Core balance to " + getBalance(target) + "."));
        target.sendMessage(Component.text("Your Core balance is now " + getBalance(target) + "."));
    }

    public void removeTargeted(Player player) {
        var eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        var hit = player.getWorld().rayTraceEntities(eye, direction, 6.0, 0.5, this::isShopVillager);
        Entity target = hit == null ? null : hit.getHitEntity();
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
                if (isShopVillager(entity)) { entity.remove(); removed++; }
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