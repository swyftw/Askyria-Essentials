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
import org.bukkit.event.EventPriority;
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
        add("armor_set", Material.NETHERITE_INGOT, 1, 2700, "Full Netherite Set (Protection V)", "armor");

        // Gear shop: max-enchanted combat gear and utility supplies. No End Crystals.
        add("gear_pearl64", Material.ENDER_PEARL, 64, 65, "64 Ender Pearls", "gear");
        add("gear_pearl16", Material.ENDER_PEARL, 16, 20, "16 Ender Pearls", "gear");
        add("gear_pearl4", Material.ENDER_PEARL, 4, 6, "4 Ender Pearls", "gear");
        add("gear_breeze64", Material.BREEZE_ROD, 64, 180, "64 Breeze Rods", "gear");
        add("gear_breeze16", Material.BREEZE_ROD, 16, 50, "16 Breeze Rods", "gear");
        add("gear_sword", Material.NETHERITE_SWORD, 1, 850, "Maxed Netherite Sword", "gear");
        add("gear_axe", Material.NETHERITE_AXE, 1, 700, "Maxed Netherite Axe", "gear");
        add("gear_pick", Material.NETHERITE_PICKAXE, 1, 650, "Maxed Netherite Pickaxe", "gear");
        add("gear_bow", Material.BOW, 1, 550, "Maxed Bow", "gear");
        add("gear_crossbow", Material.CROSSBOW, 1, 500, "Maxed Crossbow", "gear");
        add("gear_shield", Material.SHIELD, 1, 180, "Enchanted Shield", "gear");
        add("gear_gapple", Material.GOLDEN_APPLE, 64, 45, "64 Golden Apples", "gear");
        add("gear_egapple", Material.ENCHANTED_GOLDEN_APPLE, 8, 200, "8 Enchanted Golden Apples", "gear");
        add("gear_totem", Material.TOTEM_OF_UNDYING, 4, 160, "4 Totems of Undying", "gear");
        add("gear_rocket", Material.FIREWORK_ROCKET, 128, 35, "128 Firework Rockets", "gear");
        add("gear_gcarrot", Material.GOLDEN_CARROT, 64, 25, "64 Golden Carrots", "gear");
        add("gear_trident", Material.TRIDENT, 1, 400, "Maxed Trident", "gear");
        add("gear_obsidian", Material.OBSIDIAN, 64, 25, "64 Obsidian", "gear");

        // Exactly three ore-block trades, plus rare valuables.
        add("sell_iron", Material.IRON_BLOCK, 16, 4, "Iron Blocks", "seller");
        add("sell_diamond_blocks", Material.DIAMOND_BLOCK, 16, 16, "Diamond Blocks", "seller");
        add("sell_netherite", Material.NETHERITE_BLOCK, 1, 40, "Netherite Blocks", "seller");
        add("sell_totem", Material.TOTEM_OF_UNDYING, 8, 40, "Totems of Undying", "seller");
        add("sell_elytra", Material.ELYTRA, 4, 40, "Elytra", "seller");
        add("sell_heavycore", Material.HEAVY_CORE, 8, 64, "Heavy Cores", "seller");
        add("sell_star", Material.NETHER_STAR, 16, 80, "Nether Stars", "seller");
        add("sell_shulker", Material.SHULKER_BOX, 16, 48, "Shulker Boxes", "seller");
        add("sell_egg", Material.DRAGON_EGG, 1, 1000, "Dragon Egg", "seller");
        add("sell_netherite_ingots", Material.NETHERITE_INGOT, 8, 24, "Netherite Ingots", "seller");
        add("sell_diamonds", Material.DIAMOND, 32, 8, "Diamonds", "seller");
        add("sell_emeralds", Material.EMERALD, 32, 4, "Emeralds", "seller");
        add("sell_beacon", Material.BEACON, 2, 80, "Beacons", "seller");
        add("sell_mace", Material.MACE, 1, 100, "Maces", "seller");
        add("sell_tridents", Material.TRIDENT, 4, 80, "Tridents", "seller");
        add("sell_egapples", Material.ENCHANTED_GOLDEN_APPLE, 16, 80, "Enchanted Golden Apples", "seller");
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
            case "gear" -> { displayName = "Gear Villager"; profession = Villager.Profession.TOOLSMITH; }
            case "mythical" -> { displayName = "Mythical Weapons Villager"; profession = Villager.Profession.WEAPONSMITH; }
            case "seller" -> { displayName = "Seller Villager"; profession = Villager.Profession.BUTCHER; }
            default -> { player.sendMessage(Component.text("Type must be armor, gear, mythical, or seller.")); return; }
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShopVillagerInteract(PlayerInteractEntityEvent event) {
        if (!isShopVillager(event.getRightClicked())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String name = event.getRightClicked().getCustomName();
        if (name == null) return;
        openShop(player, name.startsWith("Armor") ? "armor" :
            name.startsWith("Gear") ? "gear" :
            name.startsWith("Mythical") ? "mythical" : "seller");
    }

    private void openShop(Player player, String category) {
        String title = switch (category) {
            case "armor" -> "Armor Villager";
            case "gear" -> "Gear Villager";
            case "mythical" -> "Mythical Weapons Villager";
            default -> "Seller Villager";
        };
        int size = category.equals("gear") || category.equals("seller") ? 54 : 27;
        Inventory inv = Bukkit.createInventory(null, size, Component.text(title));
        fillShopFrame(inv);
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("CORE SMP SHOP", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        infoMeta.lore(List.of(
            Component.text("Currency: physical Core Nether Stars"),
            Component.text(category.equals("seller") ? "Click an item to sell it." : "Click an item to purchase it.")
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        if (category.equals("mythical")) {
            addOrbitalIcon(inv, 10, "rain", "RAIN ORBITAL", 100);
            addOrbitalIcon(inv, 12, "stab", "STAB ORBITAL", 125);
            addOrbitalIcon(inv, 14, "lunar", "LUNAR ORBITAL", 175);
            addOrbitalIcon(inv, 16, "nuke", "NUKE ORBITAL", 250);
        } else {
            int[] armorSlots = {10, 12, 14, 16, 22};
            int[] gearSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
            };
            int[] sellerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
            };
            int index = 0;
            for (Trade trade : trades.values()) {
                if (!trade.category().equals(category)) continue;
                int[] slots = category.equals("armor") ? armorSlots :
                    category.equals("gear") ? gearSlots : sellerSlots;
                if (index < slots.length) inv.setItem(slots[index++], tradeIcon(trade, category.equals("seller")));
            }
        }
        player.openInventory(inv);
    }

    private void fillShopFrame(Inventory inv) {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == inv.getSize() / 9 - 1 || col == 0 || col == 8) {
                inv.setItem(slot, pane);
            }
        }
    }

    private ItemStack tradeIcon(Trade trade, boolean selling) {
        ItemStack icon = new ItemStack(trade.material());
        if (trade.id().startsWith("gear_")) applyGearEnchants(icon, trade.id());
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
        String category = plainTitle.startsWith("Armor") ? "armor" : plainTitle.startsWith("Gear") ? "gear" :
            plainTitle.startsWith("Mythical") ? "mythical" : "seller";
        if (category.equals("mythical")) {
            String type = clicked.getItemMeta().getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
            if (type == null) return;
            int price = switch (type) { case "rain" -> 100; case "stab" -> 125; case "lunar" -> 175; default -> 250; };
            if (!takeCore(player, price)) { player.sendMessage(Component.text("You need " + price + " Core (Nether Stars).")); return; }
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
            player.sendMessage(Component.text("You need " + trade.price() + " Core (Nether Stars) to buy that."));
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
        } else if (trade.id().startsWith("gear_") && isGearWeapon(trade.id())) {
            ItemStack item = new ItemStack(trade.material());
            applyGearEnchants(item, trade.id());
            give(player, item);
        } else {
            ItemStack item = new ItemStack(trade.material(), trade.amount());
            if (trade.category().equals("armor")) {
                item.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            }
            give(player, item);
        }
        player.sendMessage(Component.text("Purchase complete. " + trade.price() + " Core spent."));
    }

    private boolean isGearWeapon(String id) {
        return List.of("gear_sword", "gear_axe", "gear_pick", "gear_bow", "gear_crossbow", "gear_shield", "gear_trident").contains(id);
    }

    private void applyGearEnchants(ItemStack item, String id) {
        if (id.equals("gear_sword")) {
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            item.addUnsafeEnchantment(Enchantment.LOOTING, 3);
            item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
            item.addUnsafeEnchantment(Enchantment.SWEEPING_EDGE, 3);
            item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        } else if (id.equals("gear_axe")) {
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        } else if (id.equals("gear_pick")) {
            item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
            item.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        } else if (id.equals("gear_bow")) {
            item.addUnsafeEnchantment(Enchantment.POWER, 5);
            item.addUnsafeEnchantment(Enchantment.PUNCH, 2);
            item.addUnsafeEnchantment(Enchantment.FLAME, 1);
            item.addUnsafeEnchantment(Enchantment.INFINITY, 1);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        } else if (id.equals("gear_crossbow")) {
            item.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 3);
            item.addUnsafeEnchantment(Enchantment.MULTISHOT, 1);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        } else if (id.equals("gear_shield")) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        } else if (id.equals("gear_trident")) {
            item.addUnsafeEnchantment(Enchantment.IMPALING, 5);
            item.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
            item.addUnsafeEnchantment(Enchantment.CHANNELING, 1);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        }
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
        giveCore(player, trade.price());
        player.sendMessage(Component.text("Sold " + trade.amount() + " " + trade.display() + " for " + trade.price() + " Core."));
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

    private ItemStack coreItem(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Core", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        meta.lore(List.of(Component.text("CORE SMP currency", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void giveCore(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(64, remaining);
            give(player, coreItem(stack));
            remaining -= stack;
        }
    }

    private boolean takeCore(Player player, int amount) {
        if (amount <= 0) return amount == 0;
        int found = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isCore(item)) found += item.getAmount();
        }
        if (found < amount) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isCore(item)) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    private boolean isCore(ItemStack item) {
        return item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE);
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