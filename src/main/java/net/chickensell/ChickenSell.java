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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ChickenSell extends JavaPlugin implements Listener {
    private static final int SELL_AREA_END = 45;
    private static final int BLOCKS_SLOT = 45;
    private static final int SELL_BUTTON_SLOT = 49;
    private static final double LEVEL_STEP = 25_000.0;
    private static final double MAX_MULTIPLIER = 3.0;
    private final Map<Material, Double> prices = new LinkedHashMap<>();
    private final Map<Material, Category> categories = new HashMap<>();
    private Economy economy;
    private File dataFile;
    private YamlConfiguration data;

    @Override public void onEnable() {
        if (!setupEconomy()) { getLogger().severe("No Vault economy provider found."); getServer().getPluginManager().disablePlugin(this); return; }
        loadPrices(); loadData(); Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ChickenSell 1.3.0 enabled. Economy provider: " + economy.getName());
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> r = getServer().getServicesManager().getRegistration(Economy.class);
        if (r == null) return false; economy = r.getProvider(); return economy != null;
    }

    private void add(Material m, double p, Category c) { prices.put(m,p); categories.put(m,c); }

    private void loadPrices() {
        // Every placeable block is included automatically. Prices are starter balancing values.
        for (Material m : Material.values()) {
            if (!m.isBlock() || m.isAir() || !m.isItem()) continue;
            add(m, defaultBlockPrice(m), Category.BLOCKS);
        }
        // Non-block sellables used by later categories remain at 1x for now.
        add(Material.IRON_INGOT,75,Category.OTHER); add(Material.GOLD_INGOT,110,Category.OTHER);
        add(Material.DIAMOND,500,Category.OTHER); add(Material.EMERALD,350,Category.OTHER);
        add(Material.WHEAT,10,Category.OTHER); add(Material.CARROT,8,Category.OTHER); add(Material.POTATO,8,Category.OTHER);
    }

    private double defaultBlockPrice(Material m) {
        String n=m.name();
        if(n.contains("DIAMOND")) return 450; if(n.contains("EMERALD")) return 325; if(n.contains("NETHERITE")) return 900;
        if(n.contains("GOLD")) return 95; if(n.contains("IRON")) return 65; if(n.contains("COPPER")) return 30;
        if(n.contains("OBSIDIAN")) return 35; if(n.contains("QUARTZ")) return 30; if(n.contains("PRISMARINE")) return 18;
        if(n.endsWith("_LOG")||n.endsWith("_WOOD")||n.endsWith("_STEM")||n.endsWith("_HYPHAE")) return 12;
        if(n.contains("GLASS")) return 8; if(n.contains("TERRACOTTA")) return 10; if(n.contains("CONCRETE")) return 11;
        if(n.contains("WOOL")) return 9; if(n.contains("BRICKS")) return 14; if(n.contains("PLANKS")) return 8;
        if(n.contains("DEEPSLATE")||n.contains("BLACKSTONE")||n.contains("BASALT")||n.contains("TUFF")) return 7;
        if(n.contains("SAND")||n.contains("GRAVEL")) return 5; if(n.contains("DIRT")||n.contains("MUD")) return 3;
        if(n.contains("STONE")||n.contains("COBBLE")) return 6; return 8;
    }

    private void loadData(){ if(!getDataFolder().exists())getDataFolder().mkdirs(); dataFile=new File(getDataFolder(),"playerdata.yml"); data=YamlConfiguration.loadConfiguration(dataFile); }
    private void saveData(){ try{data.save(dataFile);}catch(IOException e){getLogger().severe("Could not save playerdata.yml: "+e.getMessage());} }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a){ if(!c.getName().equalsIgnoreCase("sell"))return false; if(!(s instanceof Player p)){s.sendMessage("§cBu komutu sadece oyuncular kullanabilir.");return true;} openSellMenu(p);return true; }

    private void openSellMenu(Player p){
        Inventory i=Bukkit.createInventory(new MenuHolder(MenuType.SELL,p.getUniqueId(),0),54,"§8SATIŞ MENÜSÜ");
        i.setItem(BLOCKS_SLOT,button(Material.GRASS_BLOCK,"§a§lBLOKLAR",List.of("§7Blok kategorisini aç.","","§eTıkla!")));
        i.setItem(SELL_BUTTON_SLOT,button(Material.EMERALD,"§a§lSAT",List.of("§7Üstteki 45 slota eşyalarını koy.","§7Satılabilenler otomatik hesaplanır.","","§eSatmak için tıkla!")));
        p.openInventory(i);
    }

    // First page after clicking grass: progression + another grass button to open the item list.
    private void openBlockCategory(Player p){
        Inventory i=Bukkit.createInventory(new MenuHolder(MenuType.BLOCK_CATEGORY,p.getUniqueId(),0),54,"§8BLOK KATEGORİSİ");
        fill(i,Material.GRAY_STAINED_GLASS_PANE," ");
        i.setItem(10,button(Material.GRASS_BLOCK,"§a§lBLOK EŞYALARI",List.of("§7Bu kategoride satılabilen", "§7bütün blokları ve fiyatlarını gör.","","§eListeyi açmak için tıkla!")));
        i.setItem(13,progressBook(p));
        i.setItem(16,button(Material.GOLD_BLOCK,"§6§lÇARPAN",List.of("§7Mevcut: §a"+String.format(Locale.US,"%.1fx",getMultiplier(p,Category.BLOCKS)),"§7Her §e$25,000 §7kazançta §a+0.1x","§7Maksimum: §a3.0x")));
        i.setItem(49,button(Material.ARROW,"§eGeri",List.of("§7Satış menüsüne dön.")));
        p.openInventory(i);
    }

    private void openBlockItems(Player p,int page){
        List<Material> blocks=new ArrayList<>();
        for(Material m:prices.keySet()) if(categories.get(m)==Category.BLOCKS) blocks.add(m);
        blocks.sort(Comparator.comparing(Enum::name));
        int perPage=45, maxPage=Math.max(0,(blocks.size()-1)/perPage); page=Math.max(0,Math.min(page,maxPage));
        Inventory i=Bukkit.createInventory(new MenuHolder(MenuType.BLOCK_ITEMS,p.getUniqueId(),page),54,"§8BLOKLAR §7• Sayfa "+(page+1)+"/"+(maxPage+1));
        double mult=getMultiplier(p,Category.BLOCKS); int start=page*perPage;
        for(int slot=0;slot<perPage && start+slot<blocks.size();slot++){
            Material m=blocks.get(start+slot); double base=prices.get(m);
            i.setItem(slot,button(m,"§f"+pretty(m),List.of("§7Normal fiyat: §e$"+money(base),"§7Çarpanın: §a"+String.format(Locale.US,"%.1fx",mult),"§7Satış fiyatın: §6$"+money(base*mult))));
        }
        i.setItem(45,button(Material.BOOK,"§eİlerleme",List.of("§7Kategori ekranına dön.")));
        if(page>0)i.setItem(48,button(Material.ARROW,"§eÖnceki Sayfa",List.of("§7Sayfa "+page)));
        if(page<maxPage)i.setItem(50,button(Material.ARROW,"§eSonraki Sayfa",List.of("§7Sayfa "+(page+2))));
        i.setItem(53,button(Material.BARRIER,"§cGeri",List.of("§7Blok kategorisine dön.")));
        p.openInventory(i);
    }

    private ItemStack progressBook(Player p){
        double e=getEarned(p,Category.BLOCKS); int level=getLevel(e); double mult=getMultiplier(p,Category.BLOCKS); double floor=(level-1)*LEVEL_STEP;
        String prog=level>=21?"§aMAX":"§e$"+money(e-floor)+" §7/ §e$"+money(LEVEL_STEP);
        return button(Material.BOOK,"§e§lBLOK İLERLEMESİ",List.of("§7Seviye: §f"+level+"§7/21","§7Çarpan: §a"+String.format(Locale.US,"%.1fx",mult),"§7Toplam blok kazancı: §6$"+money(e),"","§7Bu seviye: "+prog,"§7Her §e$25,000 §7= §a+0.1x","§7Maksimum: §a3.0x"));
    }

    private void fill(Inventory i,Material m,String name){ for(int s=0;s<i.getSize();s++)i.setItem(s,button(m,name,List.of())); }
    private ItemStack button(Material m,String n,List<String> lore){ ItemStack it=new ItemStack(m);ItemMeta meta=it.getItemMeta();meta.setDisplayName(n);meta.setLore(lore);it.setItemMeta(meta);return it; }

    @EventHandler public void onInventoryClick(InventoryClickEvent e){
        if(!(e.getInventory().getHolder() instanceof MenuHolder h)||!(e.getWhoClicked() instanceof Player p))return;
        if(!h.owner.equals(p.getUniqueId())){e.setCancelled(true);return;} int s=e.getRawSlot();
        if(h.type==MenuType.SELL){ if(s>=SELL_AREA_END&&s<54){e.setCancelled(true);if(s==BLOCKS_SLOT){returnSellItems(p,e.getInventory());openBlockCategory(p);}else if(s==SELL_BUTTON_SLOT)sellItems(p,e.getInventory());} return; }
        e.setCancelled(true);
        if(h.type==MenuType.BLOCK_CATEGORY){ if(s==10)openBlockItems(p,0); else if(s==49)openSellMenu(p); return; }
        if(h.type==MenuType.BLOCK_ITEMS){ if(s==45||s==53)openBlockCategory(p); else if(s==48&&h.page>0)openBlockItems(p,h.page-1); else if(s==50)openBlockItems(p,h.page+1); }
    }

    private void sellItems(Player p,Inventory i){
        double total=0,blockTotal=0;int amount=0;
        for(int s=0;s<SELL_AREA_END;s++){ItemStack it=i.getItem(s);if(it==null||it.getType().isAir())continue;Double price=prices.get(it.getType());if(price==null)continue;Category c=categories.getOrDefault(it.getType(),Category.OTHER);double line=price*it.getAmount()*getMultiplier(p,c);total+=line;if(c==Category.BLOCKS)blockTotal+=line;amount+=it.getAmount();i.setItem(s,null);}
        if(amount==0){p.sendMessage("§cSatılabilir bir eşya koymadın.");return;} economy.depositPlayer(p,total);if(blockTotal>0)addEarned(p,Category.BLOCKS,blockTotal);p.sendMessage("§a"+amount+" eşya sattın ve §e$"+money(total)+" §akazandın!");p.closeInventory();
    }

    private void addEarned(Player p,Category c,double a){String path=p.getUniqueId()+"."+c.key+".earned";double before=data.getDouble(path,0),after=before+a;int old=getLevel(before);data.set(path,after);saveData();int now=getLevel(after);if(now>old)p.sendMessage("§6§lSEVİYE ATLADIN! §eBlok seviyesi: §f"+now+" §7• §a"+String.format(Locale.US,"%.1fx",getMultiplierFromEarned(after)));}
    private double getEarned(Player p,Category c){return data.getDouble(p.getUniqueId()+"."+c.key+".earned",0);}
    private int getLevel(double e){return Math.min(21,1+(int)Math.floor(e/LEVEL_STEP));}
    private double getMultiplier(Player p,Category c){return c==Category.OTHER?1:getMultiplierFromEarned(getEarned(p,c));}
    private double getMultiplierFromEarned(double e){return Math.min(MAX_MULTIPLIER,1+(getLevel(e)-1)*.1);}
    private String pretty(Material m){String[] a=m.name().toLowerCase(Locale.ROOT).split("_");StringBuilder r=new StringBuilder();for(String x:a){if(!r.isEmpty())r.append(' ');r.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));}return r.toString();}
    private String money(double a){return String.format(Locale.US,"%,.2f",a);}

    @EventHandler public void onInventoryClose(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof MenuHolder h&&h.type==MenuType.SELL&&e.getPlayer() instanceof Player p)returnSellItems(p,e.getInventory());}
    private void returnSellItems(Player p,Inventory i){for(int s=0;s<SELL_AREA_END;s++){ItemStack it=i.getItem(s);if(it==null||it.getType().isAir())continue;i.setItem(s,null);Map<Integer,ItemStack> left=p.getInventory().addItem(it);left.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x));}}
    @Override public void onDisable(){saveData();}

    private enum Category{BLOCKS("blocks"),OTHER("other");private final String key;Category(String k){key=k;}}
    private enum MenuType{SELL,BLOCK_CATEGORY,BLOCK_ITEMS}
    private static final class MenuHolder implements InventoryHolder{private final MenuType type;private final UUID owner;private final int page;private MenuHolder(MenuType t,UUID o,int p){type=t;owner=o;page=p;}@Override public Inventory getInventory(){return null;}}
}
