package net.chickensell;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChickenSell extends JavaPlugin implements Listener {
    private static final int SELL_BUTTON_SLOT = 49;
    private static final int SELL_AREA_END = 45;

    private final Map<UUID, SellHolder> openMenus = new HashMap<>();
    private final Map<Material, Double> prices = new HashMap<>();
    private Economy economy;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("No Vault economy provider found. Disabling ChickenSell.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadStarterPrices();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ChickenSell 1.1.0 enabled. Economy provider: " + economy.getName());
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) return false;
        economy = registration.getProvider();
        return economy != null;
    }

    private void loadStarterPrices() {
        prices.put(Material.COBBLESTONE, 5.0);
        prices.put(Material.STONE, 6.0);
        prices.put(Material.DIRT, 3.0);
        prices.put(Material.GRASS_BLOCK, 8.0);
        prices.put(Material.OAK_LOG, 12.0);
        prices.put(Material.IRON_INGOT, 75.0);
        prices.put(Material.GOLD_INGOT, 110.0);
        prices.put(Material.DIAMOND, 500.0);
        prices.put(Material.EMERALD, 350.0);
        prices.put(Material.WHEAT, 10.0);
        prices.put(Material.CARROT, 8.0);
        prices.put(Material.POTATO, 8.0);
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
        SellHolder holder = new SellHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8SATIŞ MENÜSÜ");
        holder.inventory = inventory;

        ItemStack sellButton = new ItemStack(Material.EMERALD);
        ItemMeta meta = sellButton.getItemMeta();
        meta.setDisplayName("§a§lSAT");
        meta.setLore(List.of(
                "§7Üstteki boş alanlara satmak",
                "§7istediğin eşyaları koy.",
                "",
                "§eTıkla ve sat!"
        ));
        sellButton.setItemMeta(meta);
        inventory.setItem(SELL_BUTTON_SLOT, sellButton);

        openMenus.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!holder.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= SELL_AREA_END && rawSlot < 54) {
            event.setCancelled(true);
            if (rawSlot == SELL_BUTTON_SLOT) sellItems(player, event.getInventory());
        }
    }

    private void sellItems(Player player, Inventory inventory) {
        double total = 0.0;
        int soldAmount = 0;

        for (int slot = 0; slot < SELL_AREA_END; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            Double unitPrice = prices.get(item.getType());
            if (unitPrice == null) continue;

            total += unitPrice * item.getAmount();
            soldAmount += item.getAmount();
            inventory.setItem(slot, null);
        }

        if (soldAmount == 0) {
            player.sendMessage("§cSatılabilir bir eşya koymadın.");
            return;
        }

        economy.depositPlayer(player, total);
        player.sendMessage("§a" + soldAmount + " eşya sattın ve §e$" + String.format("%,.2f", total) + " §akazandın!");
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        openMenus.remove(holder.owner);
        returnItemsToPlayer(event.getPlayer(), event.getInventory());
    }

    @Override
    public void onDisable() {
        for (SellHolder holder : openMenus.values()) {
            returnItems(holder.inventory);
        }
        openMenus.clear();
    }

    private void returnItems(Inventory inventory) {
        if (inventory == null) return;
        HumanEntity viewer = inventory.getViewers().stream().findFirst().orElse(null);
        if (viewer != null) returnItemsToPlayer(viewer, inventory);
    }

    private void returnItemsToPlayer(HumanEntity human, Inventory inventory) {
        if (!(human instanceof Player player)) return;
        for (int slot = 0; slot < SELL_AREA_END; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private static final class SellHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private SellHolder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
