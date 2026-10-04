package net.chickensell;

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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChickenSell extends JavaPlugin implements Listener {
    private final Map<UUID, SellHolder> openMenus = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ChickenSell 1.0.1 enabled on Paper 26.2.");
    }

    @Override
    public void onDisable() {
        for (SellHolder holder : openMenus.values()) {
            returnItems(holder.inventory);
        }
        openMenus.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sell")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        openSellMenu(player);
        return true;
    }

    private void openSellMenu(Player player) {
        SellHolder holder = new SellHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8Sell Items");
        holder.inventory = inventory;

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§eChickenSell Test");
        meta.setLore(java.util.List.of("§7Put items in the top 45 slots.", "§7Close the menu to get them back.", "§eSelling will be added next."));
        info.setItemMeta(meta);
        inventory.setItem(49, info);

        openMenus.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder)) return;
        if (event.getRawSlot() >= 45 && event.getRawSlot() < 54) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        openMenus.remove(holder.owner);
        returnItemsToPlayer(event.getPlayer(), event.getInventory());
    }

    private void returnItems(Inventory inventory) {
        if (inventory == null) return;
        HumanEntity viewer = inventory.getViewers().stream().findFirst().orElse(null);
        if (viewer != null) returnItemsToPlayer(viewer, inventory);
    }

    private void returnItemsToPlayer(HumanEntity human, Inventory inventory) {
        if (!(human instanceof Player player)) return;
        for (int slot = 0; slot < 45; slot++) {
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
