package net.chickensell;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ChickenSell extends JavaPlugin implements Listener {
    private static final int SELL_AREA_END = 45;
    private static final int BLOCKS_SLOT = 45;
    private static final int PROGRESS_SLOT = 47;
    private static final int SELL_BUTTON_SLOT = 49;
    private static final double LEVEL_STEP = 25_000.0;
    private static final double MAX_MULTIPLIER = 3.0;

    private final Map<Material, Double> prices = new LinkedHashMap<>();
    private final Map<Material, Category> categories = new HashMap<>();
    private Economy economy;
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("No Vault economy provider found. Disabling ChickenSell.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadPrices();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ChickenSell 1.2.0 enabled. Economy provider: " + economy.getName());
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) return false;
        economy = registration.getProvider();
        return economy != null;
    }

    private void add(Material material, double price, Category category) {
        prices.put(material, price);
        categories.put(material, category);
    }

    private void loadPrices() {
        add(Material.STONE, 6, Category.BLOCKS);
        add(Material.COBBLESTONE, 5, Category.BLOCKS);
        add(Material.DEEPSLATE, 7, Category.BLOCKS);
        add(Material.COBBLED_DEEPSLATE, 6, Category.BLOCKS);
        add(Material.DIRT, 3, Category.BLOCKS);
        add(Material.GRASS_BLOCK, 8, Category.BLOCKS);
        add(Material.SAND, 5, Category.BLOCKS);
        add(Material.RED_SAND, 6, Category.BLOCKS);
        add(Material.GRAVEL, 5, Category.BLOCKS);
        add(Material.CLAY, 9, Category.BLOCKS);
        add(Material.NETHERRACK, 4, Category.BLOCKS);
        add(Material.END_STONE, 10, Category.BLOCKS);
        add(Material.OBSIDIAN, 35, Category.BLOCKS);
        add(Material.OAK_LOG, 12, Category.BLOCKS);
        add(Material.SPRUCE_LOG, 12, Category.BLOCKS);
        add(Material.BIRCH_LOG, 12, Category.BLOCKS);
        add(Material.JUNGLE_LOG, 13, Category.BLOCKS);
        add(Material.ACACIA_LOG, 13, Category.BLOCKS);
        add(Material.DARK_OAK_LOG, 13, Category.BLOCKS);
        add(Material.MANGROVE_LOG, 14, Category.BLOCKS);
        add(Material.CHERRY_LOG, 14, Category.BLOCKS);
        add(Material.PALE_OAK_LOG, 14, Category.BLOCKS);
        add(Material.BASALT, 8, Category.BLOCKS);
        add(Material.BLACKSTONE, 8, Category.BLOCKS);
        add(Material.CALCITE, 10, Category.BLOCKS);
        add(Material.TUFF, 8, Category.BLOCKS);
        add(Material.PRISMARINE, 18, Category.BLOCKS);
        add(Material.QUARTZ_BLOCK, 30, Category.BLOCKS);

        add(Material.IRON_INGOT, 75, Category.OTHER);
        add(Material.GOLD_INGOT, 110, Category.OTHER);
        add(Material.DIAMOND, 500, Category.OTHER);
        add(Material.EMERALD, 350, Category.OTHER);
        add(Material.WHEAT, 10, Category.OTHER);
        add(Material.CARROT, 8, Category.OTHER);
        add(Material.POTATO, 8, Category.OTHER);
    }

    private void loadData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "playerdata.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save playerdata.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sell")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cBu komutu sadece oyuncular kullanabilir.");
            return true;
        }
        openSellMenu(player);
        return true;
    }

    private void openSellMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.SELL, player.getUniqueId()), 54, "§8SATIŞ MENÜSÜ");
        inventory.setItem(BLOCKS_SLOT, button(Material.GRASS_BLOCK, "§a§lBLOKLAR", List.of("§7Blok fiyatlarını görüntüle.", "", "§eAçmak için tıkla!")));
        inventory.setItem(PROGRESS_SLOT, progressBook(player));
        inventory.setItem(SELL_BUTTON_SLOT, button(Material.EMERALD, "§a§lSAT", List.of("§7Üstteki 45 slota eşyalarını koy.", "§7Satılabilenler otomatik hesaplanır.", "", "§eSatmak için tıkla!")));
        player.openInventory(inventory);
    }

    private void openBlocksMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.BLOCKS, player.getUniqueId()), 54, "§8BLOKLAR • SATIŞ FİYATLARI");
        int slot = 0;
        double multiplier = getMultiplier(player, Category.BLOCKS);
        for (Map.Entry<Material, Double> entry : prices.entrySet()) {
            if (categories.get(entry.getKey()) != Category.BLOCKS || slot >= 45) continue;
            double actual = entry.getValue() * multiplier;
            inventory.setItem(slot++, button(entry.getKey(), "§f" + pretty(entry.getKey()), List.of(
                    "§7Normal fiyat: §e$" + money(entry.getValue()),
                    "§7Çarpanın: §a" + String.format(Locale.US, "%.1fx", multiplier),
                    "§7Senin fiyatın: §6$" + money(actual),
                    "",
                    "§8Bu ekran sadece fiyat listesidir."
            )));
        }
        inventory.setItem(49, progressBook(player));
        inventory.setItem(53, button(Material.ARROW, "§eGeri", List.of("§7Satış menüsüne dön.")));
        player.openInventory(inventory);
    }

    private ItemStack progressBook(Player player) {
        double earned = getEarned(player, Category.BLOCKS);
        int level = getLevel(earned);
        double multiplier = getMultiplier(player, Category.BLOCKS);
        double currentFloor = (level - 1) * LEVEL_STEP;
        double nextTarget = level >= 21 ? currentFloor : level * LEVEL_STEP;
        String progress = level >= 21 ? "§aMAX" : "§e$" + money(earned - currentFloor) + " §7/ §e$" + money(LEVEL_STEP);
        return button(Material.BOOK, "§e§lBLOK İLERLEMESİ", List.of(
                "§7Seviye: §f" + level + "§7/21",
                "§7Çarpan: §a" + String.format(Locale.US, "%.1fx", multiplier),
                "§7Toplam blok kazancı: §6$" + money(earned),
                "",
                "§7Bu seviye: " + progress,
                level >= 21 ? "§aMaksimum çarpana ulaştın!" : "§7Sonraki hedef: §e$" + money(nextTarget),
                "§7Her §e$25,000 §7= §a+0.1x",
                "§7Maksimum: §a3.0x"
        ));
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (holder.type == MenuType.SELL) {
            if (rawSlot >= SELL_AREA_END && rawSlot < 54) {
                event.setCancelled(true);
                if (rawSlot == BLOCKS_SLOT) {
                    returnSellItems(player, event.getInventory());
                    openBlocksMenu(player);
                } else if (rawSlot == PROGRESS_SLOT) {
                    returnSellItems(player, event.getInventory());
                    openBlocksMenu(player);
                } else if (rawSlot == SELL_BUTTON_SLOT) {
                    sellItems(player, event.getInventory());
                }
            }
            return;
        }

        event.setCancelled(true);
        if (holder.type == MenuType.BLOCKS && rawSlot == 53) openSellMenu(player);
    }

    private void sellItems(Player player, Inventory inventory) {
        double total = 0;
        double blockBaseTotal = 0;
        int soldAmount = 0;

        for (int slot = 0; slot < SELL_AREA_END; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            Double unitPrice = prices.get(item.getType());
            if (unitPrice == null) continue;

            double base = unitPrice * item.getAmount();
            Category category = categories.getOrDefault(item.getType(), Category.OTHER);
            double lineTotal = base * getMultiplier(player, category);
            total += lineTotal;
            if (category == Category.BLOCKS) blockBaseTotal += lineTotal;
            soldAmount += item.getAmount();
            inventory.setItem(slot, null);
        }

        if (soldAmount == 0) {
            player.sendMessage("§cSatılabilir bir eşya koymadın.");
            return;
        }

        economy.depositPlayer(player, total);
        if (blockBaseTotal > 0) addEarned(player, Category.BLOCKS, blockBaseTotal);
        player.sendMessage("§a" + soldAmount + " eşya sattın ve §e$" + money(total) + " §akazandın!");
        player.closeInventory();
    }

    private void addEarned(Player player, Category category, double amount) {
        String path = player.getUniqueId() + "." + category.key + ".earned";
        double before = data.getDouble(path, 0);
        int oldLevel = getLevel(before);
        double after = before + amount;
        data.set(path, after);
        saveData();
        int newLevel = getLevel(after);
        if (newLevel > oldLevel) {
            player.sendMessage("§6§lSEVİYE ATLADIN! §eBlok seviyesi: §f" + newLevel + " §7• §a" + String.format(Locale.US, "%.1fx", getMultiplierFromEarned(after)));
        }
    }

    private double getEarned(Player player, Category category) {
        return data.getDouble(player.getUniqueId() + "." + category.key + ".earned", 0);
    }

    private int getLevel(double earned) {
        return Math.min(21, 1 + (int) Math.floor(earned / LEVEL_STEP));
    }

    private double getMultiplier(Player player, Category category) {
        if (category == Category.OTHER) return 1.0;
        return getMultiplierFromEarned(getEarned(player, category));
    }

    private double getMultiplierFromEarned(double earned) {
        return Math.min(MAX_MULTIPLIER, 1.0 + (getLevel(earned) - 1) * 0.1);
    }

    private String pretty(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String money(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (holder.type == MenuType.SELL && event.getPlayer() instanceof Player player) returnSellItems(player, event.getInventory());
    }

    private void returnSellItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < SELL_AREA_END; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private enum Category {
        BLOCKS("blocks"), OTHER("other");
        private final String key;
        Category(String key) { this.key = key; }
    }

    private enum MenuType { SELL, BLOCKS }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final UUID owner;
        private MenuHolder(MenuType type, UUID owner) {
            this.type = type;
            this.owner = owner;
        }
        @Override
        public Inventory getInventory() { return null; }
    }
}
