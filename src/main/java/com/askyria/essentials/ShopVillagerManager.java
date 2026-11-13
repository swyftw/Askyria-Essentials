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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

public final class ShopVillagerManager implements Listener {
    private final AskyriaEssentials plugin;
    private final NamespacedKey shopVillagerKey;
    private final NamespacedKey shardKey;
    private final NamespacedKey orbitalKey;
    private final NamespacedKey tradeKey;
    private final NamespacedKey switcherKey;
    private final NamespacedKey switcherExpiryKey;
    private final Map<java.util.UUID, Long> switcherCooldowns = new HashMap<>();
    private final Map<java.util.UUID, BukkitTask> switcherReturnTasks = new HashMap<>();
    private final Map<String, Trade> trades = new java.util.LinkedHashMap<>();
    private final Map<java.util.UUID, String> editing = new HashMap<>();

    private record Trade(String id, Material material, int amount, int price, String display, String category) {}

    public ShopVillagerManager(AskyriaEssentials plugin) {
        this.plugin = plugin;
        this.shopVillagerKey = new NamespacedKey(plugin, "core_shop_villager");
        this.shardKey = new NamespacedKey(plugin, "core_shard");
        this.orbitalKey = new NamespacedKey(plugin, "orbital_type");
        this.tradeKey = new NamespacedKey(plugin, "core_shop_trade");
        this.switcherKey = new NamespacedKey(plugin, "switcher_form");
        this.switcherExpiryKey = new NamespacedKey(plugin, "switcher_expiry");
        add("armor_helmet", Material.NETHERITE_HELMET, 1, 40, "Netherite Helmet (Protection V)", "armor");
        add("armor_chest", Material.NETHERITE_CHESTPLATE, 1, 50, "Netherite Chestplate (Protection V)", "armor");
        add("armor_legs", Material.NETHERITE_LEGGINGS, 1, 45, "Netherite Leggings (Protection V)", "armor");
        add("armor_boots", Material.NETHERITE_BOOTS, 1, 40, "Netherite Boots (Protection V)", "armor");
        add("armor_set", Material.NETHERITE_INGOT, 1, 150, "Full Netherite Set (Protection V)", "armor");

        // Four pre-filled combat kits replace the old Gear shop.
        add("kit_diamond_mace", Material.SHULKER_BOX, 1, 40, "Diamond Mace Kit", "kits");
        add("kit_smp", Material.SHULKER_BOX, 1, 50, "SMP Kit", "kits");
        add("kit_ely_mace", Material.SHULKER_BOX, 1, 65, "Ely Mace Kit", "kits");
        add("kit_spear_mace", Material.SHULKER_BOX, 1, 75, "Spear Mace Kit", "kits");

        // Exactly three ore-block trades, plus rare valuables.
        add("sell_diamond_blocks", Material.DIAMOND_BLOCK, 16, 2, "Diamond Blocks", "seller");
        add("sell_netherite", Material.NETHERITE_BLOCK, 1, 5, "Netherite Blocks", "seller");
        add("sell_elytra", Material.ELYTRA, 4, 4, "Elytra", "seller");
        add("sell_shulker", Material.SHULKER_BOX, 16, 3, "Shulker Boxes", "seller");
        add("sell_egg", Material.DRAGON_EGG, 1, 40, "Dragon Egg", "seller");
        add("sell_netherite_ingots", Material.NETHERITE_INGOT, 8, 2, "Netherite Ingots", "seller");
        add("sell_beacon", Material.BEACON, 2, 10, "Beacons", "seller");
        add("sell_mace", Material.MACE, 1, 10, "Maces", "seller");
        add("sell_tridents", Material.TRIDENT, 4, 6, "Tridents", "seller");
        add("sell_egapples", Material.ENCHANTED_GOLDEN_APPLE, 16, 8, "Enchanted Golden Apples", "seller");
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
            case "kits" -> { displayName = "Kit Villager"; profession = Villager.Profession.TOOLSMITH; }
            case "mythical" -> { displayName = "Mythical Weapons Villager"; profession = Villager.Profession.WEAPONSMITH; }
            case "seller" -> { displayName = "Seller Villager"; profession = Villager.Profession.BUTCHER; }
            default -> { player.sendMessage(Component.text("Type must be armor, kits, mythical, or seller.")); return; }
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
            name.startsWith("Kit") ? "kits" :
            name.startsWith("Mythical") ? "mythical" : "seller");
    }

    public void openShop(Player player, String category) {
        String title = switch (category) {
            case "armor" -> "Armor Shop";
            case "kits" -> "Kits Shop";
            case "mythical" -> "Mythical Shop";
            default -> "Seller Shop";
        };
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, Component.text(title));
        fillShopFrame(inv);
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("CORE SMP SHOP", net.kyori.adventure.text.format.NamedTextColor.AQUA)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        infoMeta.lore(List.of(
            Component.text("Currency: Core Nether Stars", net.kyori.adventure.text.format.NamedTextColor.GOLD),
            Component.text(category.equals("seller") ? "Sell items here and receive Cores." : "Click an item to purchase it.",
                category.equals("seller") ? net.kyori.adventure.text.format.NamedTextColor.GREEN : net.kyori.adventure.text.format.NamedTextColor.YELLOW)
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        if (category.equals("mythical")) {
            String[] ids = {"rain", "stab", "lunar", "nuke", "switcher"};
            String[] names = {"RAIN ORBITAL", "STAB ORBITAL", "LUNAR ORBITAL", "NUKE ORBITAL", "SWITCHER"};
            int[] prices = {15, 22, 30, 45, 75};
            int[] defaults = {20, 22, 24, 31, 40};
            Map<String, Integer> layout = loadLayout(category);
            for (int i = 0; i < ids.length; i++) addOrbitalIcon(inv,
                layout.getOrDefault(ids[i], defaults[i]), ids[i], names[i], prices[i]);
        } else {
            int[] defaults = defaultSlots(category);
            Map<String, Integer> layout = loadLayout(category);
            int index = 0;
            for (Trade trade : trades.values()) {
                if (!trade.category().equals(category)) continue;
                int slot = layout.getOrDefault(trade.id(), index < defaults.length ? defaults[index] : -1);
                if (slot >= 9 && slot < 45) inv.setItem(slot, tradeIcon(trade, category.equals("seller")));
                index++;
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
        
        if (trade.id().equals("armor_helmet") || trade.id().equals("armor_chest")
                || trade.id().equals("armor_legs") || trade.id().equals("armor_boots")) {
            icon.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
            icon.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        }
        if (trade.id().equals("armor_set")) icon.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
        ItemMeta meta = icon.getItemMeta();
        var nameColor = switch (trade.category()) {
            case "armor" -> net.kyori.adventure.text.format.NamedTextColor.AQUA;
            case "kits" -> net.kyori.adventure.text.format.NamedTextColor.GREEN;
            case "seller" -> net.kyori.adventure.text.format.NamedTextColor.GOLD;
            default -> net.kyori.adventure.text.format.NamedTextColor.WHITE;
        };
        meta.displayName(Component.text((selling ? "SELL: " : "") + trade.display(), nameColor)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        meta.getPersistentDataContainer().set(tradeKey, PersistentDataType.STRING, trade.id());
        meta.lore(List.of(
            Component.text("Quantity: " + trade.amount(), net.kyori.adventure.text.format.NamedTextColor.GRAY),
            Component.text(selling ? "YOU RECEIVE: " + trade.price() + " CORES" : "Price: " + trade.price() + " Cores",
                selling ? net.kyori.adventure.text.format.NamedTextColor.GREEN : net.kyori.adventure.text.format.NamedTextColor.YELLOW),
            Component.text("Click to " + (selling ? "sell" : "purchase"),
                net.kyori.adventure.text.format.NamedTextColor.AQUA)
        ));
        icon.setItemMeta(meta);
        return icon;
    }

    private void addOrbitalIcon(Inventory inv, int slot, String type, String display, int price) {
        ItemStack icon = type.equals("switcher") ? createSwitcherSword() : createOrbital(type);
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.text("Price: " + price + " Core", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(orbitalKey, PersistentDataType.STRING, type);
        icon.setItemMeta(meta);
        inv.setItem(slot, icon);
    }

    public void openEditor(Player player, String category) {
        if (!List.of("armor", "kits", "mythical", "seller").contains(category)) {
            player.sendMessage(Component.text("Use armor, kits, mythical, or seller."));
            return;
        }
        String title = "Edit " + switch (category) {
            case "armor" -> "Armor";
            case "kits" -> "Kits";
            case "mythical" -> "Mythical";
            default -> "Seller";
        } + " Shop";
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));
        int[] defaults = defaultSlots(category);
        Map<String, Integer> layout = loadLayout(category);
        if (category.equals("mythical")) {
            String[] ids = {"rain", "stab", "lunar", "nuke", "switcher"};
            String[] names = {"RAIN ORBITAL", "STAB ORBITAL", "LUNAR ORBITAL", "NUKE ORBITAL", "SWITCHER"};
            int[] prices = {15, 22, 30, 45, 75};
            for (int i = 0; i < ids.length; i++) {
                ItemStack icon = orbitalEditorIcon(ids[i], names[i], prices[i]);
                inv.setItem(layout.getOrDefault(ids[i], defaults[i]), icon);
            }
        } else {
            int index = 0;
            for (Trade trade : trades.values()) {
                if (!trade.category().equals(category)) continue;
                int slot = layout.getOrDefault(trade.id(), index < defaults.length ? defaults[index] : -1);
                if (slot >= 9 && slot < 45) inv.setItem(slot, tradeIcon(trade, category.equals("seller")));
                index++;
            }
        }
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(Component.text("Cancel"));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(45, cancel);
        ItemStack save = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = save.getItemMeta();
        saveMeta.displayName(Component.text("SAVE LAYOUT"));
        saveMeta.lore(List.of(Component.text("Click to save this shop arrangement")));
        save.setItemMeta(saveMeta);
        inv.setItem(49, save);
        editing.put(player.getUniqueId(), category);
        player.openInventory(inv);
        player.sendMessage(Component.text("Move shop items around, then click SAVE LAYOUT."));
    }

    private int[] defaultSlots(String category) {
        if (category.equals("armor")) return new int[]{20, 22, 24, 30, 32};
        if (category.equals("mythical")) return new int[]{20, 22, 24, 31, 40};
        if (category.equals("kits")) return new int[]{
            10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34
        };
        return new int[]{
            10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43
        };
    }

    private Map<String, Integer> loadLayout(String category) {
        Map<String, Integer> result = new HashMap<>();
        var section = plugin.getConfig().getConfigurationSection("shop-layout." + category);
        if (section != null) for (String id : section.getKeys(false)) {
            int slot = section.getInt(id, -1);
            if (slot >= 9 && slot < 45) result.put(id, slot);
        }
        return result;
    }

    private ItemStack orbitalEditorIcon(String type, String name, int price) {
        ItemStack icon = type.equals("switcher") ? createSwitcherSword() : createOrbital(type);
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.text("Price: " + price + " Core", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(orbitalKey, PersistentDataType.STRING, type);
        icon.setItemMeta(meta);
        return icon;
    }

    private void saveEditor(Player player, Inventory inv, String category) {
        plugin.getConfig().set("shop-layout." + category, null);
        for (int slot = 9; slot < 45; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer().get(tradeKey, PersistentDataType.STRING);
            if (id != null) plugin.getConfig().set("shop-layout." + category + "." + id, slot);
            String orbital = item.getItemMeta().getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
            if (orbital != null) plugin.getConfig().set("shop-layout." + category + "." + orbital, slot);
        }
        plugin.saveConfig();
        editing.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(Component.text("Saved " + category + " shop layout."));
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.startsWith("Edit ") && title.endsWith(" Shop")) {
            if (!(event.getWhoClicked() instanceof Player player)) { event.setCancelled(true); return; }
            String category = editing.get(player.getUniqueId());
            if (category == null) { event.setCancelled(true); return; }
            int raw = event.getRawSlot();
            if (raw == 49 && event.getClickedInventory() == event.getView().getTopInventory()) {
                event.setCancelled(true);
                saveEditor(player, event.getView().getTopInventory(), category);
                return;
            }
            if (raw == 45 && event.getClickedInventory() == event.getView().getTopInventory()) {
                event.setCancelled(true);
                editing.remove(player.getUniqueId());
                player.closeInventory();
                return;
            }
            if (event.getClickedInventory() == event.getView().getBottomInventory()
                    || raw < 9 || raw >= 45 || event.isShiftClick()
                    || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.DOUBLE_CLICK
                    || event.getClick() == ClickType.SWAP_OFFHAND) {
                event.setCancelled(true);
            }
            return;
        }
        if (!title.endsWith(" Shop")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        String plainTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        String category = plainTitle.startsWith("Armor") ? "armor" : plainTitle.startsWith("Kit") ? "kits" :
            plainTitle.startsWith("Mythical") ? "mythical" : "seller";
        if (category.equals("mythical")) {
            String type = clicked.getItemMeta().getPersistentDataContainer().get(orbitalKey, PersistentDataType.STRING);
            if (type == null) return;
            int price = switch (type) { case "rain" -> 15; case "stab" -> 22; case "lunar" -> 30; case "nuke" -> 45; case "switcher" -> 75; default -> 45; };
            if (!takeCore(player, price)) { player.sendMessage(Component.text("You need " + price + " Core (Nether Stars).")); return; }
            ItemStack reward = type.equals("switcher") ? createSwitcherSword() : createOrbital(type);
            player.getInventory().addItem(reward).values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            player.sendMessage(Component.text(type.equals("switcher") ? "Purchased SWITCHER. Sneak + right-click to transform it." : "Purchased " + type.toUpperCase() + " Orbital."));
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
        if (title.startsWith("Edit ") && title.endsWith(" Shop")) {
            for (int raw : event.getRawSlots()) {
                if (raw < 9 || raw >= 45) { event.setCancelled(true); return; }
            }
            return;
        }
        if (title.endsWith(" Shop")) event.setCancelled(true);
    }


    private ItemStack kitPotion(PotionType type, int amount) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, amount);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(type);
        potion.setItemMeta(meta);
        return potion;
    }

    private ItemStack kitEnchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }

    private ItemStack kitArmor(Material material, boolean diamond) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return item;
    }

    private List<ItemStack> kitContents(String id) {
        List<ItemStack> items = new ArrayList<>();
        boolean diamond = id.equals("kit_diamond_mace");
        Material armor = diamond ? Material.DIAMOND_HELMET : Material.NETHERITE_HELMET;
        items.add(kitArmor(armor, diamond));
        items.add(kitArmor(diamond ? Material.DIAMOND_CHESTPLATE : Material.NETHERITE_CHESTPLATE, diamond));
        items.add(kitArmor(diamond ? Material.DIAMOND_LEGGINGS : Material.NETHERITE_LEGGINGS, diamond));
        items.add(kitArmor(diamond ? Material.DIAMOND_BOOTS : Material.NETHERITE_BOOTS, diamond));

        if (id.equals("kit_diamond_mace")) {
            ItemStack pick = kitEnchanted(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5);
            pick.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            pick.addUnsafeEnchantment(Enchantment.MENDING, 1);
            items.add(pick);
            items.add(new ItemStack(Material.OAK_LOG, 64));
            items.add(new ItemStack(Material.PACKED_ICE, 64));
            items.add(new ItemStack(Material.WATER_BUCKET, 3));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING));
            items.add(new ItemStack(Material.COBWEB, 64));
            items.add(new ItemStack(Material.ARROW, 64));
        } else if (id.equals("kit_ely_mace")) {
            ItemStack elytra = new ItemStack(Material.ELYTRA);
            elytra.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            elytra.addUnsafeEnchantment(Enchantment.MENDING, 1);
            items.add(elytra);
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 4));
        } else if (id.equals("kit_spear_mace")) {
            ItemStack trident = kitEnchanted(Material.TRIDENT, Enchantment.IMPALING, 5);
            trident.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
            trident.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            trident.addUnsafeEnchantment(Enchantment.MENDING, 1);
            items.add(trident);
            items.add(new ItemStack(Material.IRON_INGOT, 64));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 4));
        } else {
            items.add(new ItemStack(Material.SHIELD));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 4));
            items.add(new ItemStack(Material.COBWEB, 64));
        }

        ItemStack sword = kitEnchanted(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5);
        sword.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
        sword.addUnsafeEnchantment(Enchantment.LOOTING, 3);
        sword.addUnsafeEnchantment(Enchantment.MENDING, 1);
        items.add(sword);

        ItemStack axe = kitEnchanted(Material.NETHERITE_AXE, Enchantment.SHARPNESS, 5);
        axe.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        axe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        axe.addUnsafeEnchantment(Enchantment.MENDING, 1);
        items.add(axe);

        ItemStack mace = kitEnchanted(Material.MACE, Enchantment.DENSITY, 5);
        mace.addUnsafeEnchantment(Enchantment.WIND_BURST, 2);
        mace.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        mace.addUnsafeEnchantment(Enchantment.MENDING, 1);
        items.add(mace);

        items.add(new ItemStack(Material.ENDER_PEARL, 16));
        items.add(new ItemStack(Material.GOLDEN_APPLE, 64));
        items.add(kitPotion(PotionType.STRENGTH, 1));
        items.add(kitPotion(PotionType.STRENGTH, 1));
        items.add(kitPotion(PotionType.SWIFTNESS, 1));
        items.add(kitPotion(PotionType.SWIFTNESS, 1));
        items.add(kitPotion(PotionType.FIRE_RESISTANCE, 1));
        items.add(kitPotion(PotionType.FIRE_RESISTANCE, 1));
        items.add(kitPotion(PotionType.STRONG_HEALING, 1));
        items.add(kitPotion(PotionType.STRONG_HEALING, 1));
        return items;
    }

    private ItemStack createKitShulker(String id) {
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        List<ItemStack> items = kitContents(id);
        // Use the shulker block state's live inventory before writing it back
        // to BlockStateMeta. This ensures the item actually carries its contents.
        Inventory contents = state.getInventory();
        contents.clear();
        for (int i = 0; i < items.size() && i < contents.getSize(); i++) {
            ItemStack packed = items.get(i);
            if (packed != null && !packed.getType().isAir()) contents.setItem(i, packed.clone());
        }
        String name = switch (id) {
            case "kit_diamond_mace" -> "§b§lDIAMOND MACE KIT";
            case "kit_smp" -> "§d§lSMP KIT";
            case "kit_ely_mace" -> "§5§lELY MACE KIT";
            default -> "§5§lSPEAR MACE KIT";
        };
        meta.setDisplayName(name);
        meta.setLore(List.of("§7CORE SMP combat kit", "§ePre-filled shulker box", "§aContents packed inside"));
        meta.setBlockState(state);
        box.setItemMeta(meta);
        return box;
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
        } else if (trade.category().equals("kits")) {
            give(player, createKitShulker(trade.id()));
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

    private ItemStack createSwitcherSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        item.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
        item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
        item.addUnsafeEnchantment(Enchantment.LOOTING, 3);
        item.addUnsafeEnchantment(Enchantment.SWEEPING_EDGE, 3);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("SWITCHER", net.kyori.adventure.text.format.NamedTextColor.GOLD)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        meta.lore(List.of(
            Component.text("MAXED OUT", net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true),
            Component.text("Sneak + Right Click", net.kyori.adventure.text.format.NamedTextColor.YELLOW),
            Component.text("Switch into a powerful mace form", net.kyori.adventure.text.format.NamedTextColor.GRAY),
            Component.text("Mace Form: 2 Minutes", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE),
            Component.text("Mace Damage: 2x", net.kyori.adventure.text.format.NamedTextColor.RED),
            Component.text("Cooldown: 5 Minutes", net.kyori.adventure.text.format.NamedTextColor.GOLD)
        ));
        meta.getPersistentDataContainer().set(switcherKey, PersistentDataType.STRING, "sword");
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSwitcherMace(long expiresAt) {
        ItemStack item = new ItemStack(Material.MACE);
        item.addUnsafeEnchantment(Enchantment.DENSITY, 5);
        item.addUnsafeEnchantment(Enchantment.WIND_BURST, 3);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("SWITCHER • MACE FORM", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        meta.lore(List.of(Component.text("Deals double damage"),
            Component.text("Returns to sword form after 2 minutes")));
        meta.getPersistentDataContainer().set(switcherKey, PersistentDataType.STRING, "mace");
        meta.getPersistentDataContainer().set(switcherExpiryKey, PersistentDataType.LONG, expiresAt);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onSwitcherActivate(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta()) return;
        String form = held.getItemMeta().getPersistentDataContainer().get(switcherKey, PersistentDataType.STRING);
        if (!"sword".equals(form)) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        long expiry = switcherCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (expiry > now) {
            long seconds = (expiry - now + 999L) / 1000L;
            player.sendActionBar(Component.text("SWITCHER COOLDOWN • " + (seconds / 60) + "m " + (seconds % 60) + "s"));
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        player.getInventory().setItem(slot, createSwitcherMace(now + 120_000L));
        java.util.UUID id = player.getUniqueId();
        switcherCooldowns.put(id, now + 300_000L);
        BukkitTask previous = switcherReturnTasks.remove(id);
        if (previous != null) previous.cancel();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null) restoreSwitcherSword(online);
            switcherReturnTasks.remove(id);
        }, 2400L);
        switcherReturnTasks.put(id, task);
        player.sendMessage(Component.text("SWITCHER activated. Mace form lasts 2 minutes; cooldown is 5 minutes."));
    }

    private ItemStack restoreExpiredSwitcher(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String form = pdc.get(switcherKey, PersistentDataType.STRING);
        Long expiresAt = pdc.get(switcherExpiryKey, PersistentDataType.LONG);
        if (!"mace".equals(form) || expiresAt == null || System.currentTimeMillis() < expiresAt) return item;
        return createSwitcherSword();
    }

    @EventHandler
    public void onSwitcherInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ItemStack restored = restoreExpiredSwitcher(item);
            if (restored != item) inventory.setItem(slot, restored);
        }
    }

    @EventHandler
    public void onSwitcherItemPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        ItemStack restored = restoreExpiredSwitcher(item);
        if (restored != item) event.getItem().setItemStack(restored);
    }

    private void restoreSwitcherSword(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            String form = item.getItemMeta().getPersistentDataContainer().get(switcherKey, PersistentDataType.STRING);
            if ("mace".equals(form)) {
                player.getInventory().setItem(slot, createSwitcherSword());
                player.sendMessage(Component.text("SWITCHER returned to sword form."));
                return;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.hasItemMeta() && "mace".equals(offhand.getItemMeta().getPersistentDataContainer().get(switcherKey, PersistentDataType.STRING))) {
            player.getInventory().setItemInOffHand(createSwitcherSword());
            player.sendMessage(Component.text("SWITCHER returned to sword form."));
        }
    }

    @EventHandler
    public void onSwitcherMaceDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;
        if ("mace".equals(weapon.getItemMeta().getPersistentDataContainer().get(switcherKey, PersistentDataType.STRING))) {
            event.setDamage(event.getDamage() * 2.0);
        }
    }

    public long getSwitcherCooldownSeconds(Player player) {
        return Math.max(0L, (switcherCooldowns.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis() + 999L) / 1000L);
    }

    private ItemStack createOrbital(String type) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        ItemMeta meta = item.getItemMeta();
        var color = switch (type) {
            case "rain" -> net.kyori.adventure.text.format.NamedTextColor.AQUA;
            case "stab" -> net.kyori.adventure.text.format.NamedTextColor.RED;
            case "nuke" -> net.kyori.adventure.text.format.NamedTextColor.DARK_RED;
            default -> net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
        };
        String name = switch (type) {
            case "rain" -> "RAIN ORBITAL";
            case "stab" -> "STAB ORBITAL";
            case "nuke" -> "NUKE ORBITAL";
            default -> "LUNAR ORBITAL";
        };
        String ability = switch (type) {
            case "rain" -> "Wide-area orbital barrage";
            case "stab" -> "Focused vertical orbital strike";
            case "nuke" -> "Largest area-damage orbital";
            default -> "Massive spherical orbital strike";
        };
        meta.displayName(Component.text(name, color)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        meta.lore(List.of(
            Component.text("MAXED ORBITAL WEAPON", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true),
            Component.text(ability, net.kyori.adventure.text.format.NamedTextColor.GRAY),
            Component.text("Right-click to launch", net.kyori.adventure.text.format.NamedTextColor.YELLOW),
            Component.text("Single-use • consumed on launch", net.kyori.adventure.text.format.NamedTextColor.RED)
        ));
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