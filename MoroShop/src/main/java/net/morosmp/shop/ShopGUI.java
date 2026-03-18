package net.morosmp.shop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShopGUI implements Listener {
    private final MoroShop plugin;

    public ShopGUI(MoroShop plugin) { this.plugin = plugin; }

    public void openMainMenu(Player player) {
        int size = plugin.getConfig().getInt("main-menu.size", 27);
        String title = Utils.color(plugin.getConfig().getString("main-menu.title"), true);
        Inventory inv = Bukkit.createInventory(null, size, title);

        ConfigurationSection cats = plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (cats != null) {
            for (String key : cats.getKeys(false)) {
                int slot = cats.getInt(key + ".slot");
                Material mat = Material.matchMaterial(cats.getString(key + ".material", "DIRT"));
                if (mat == null) mat = Material.DIRT;
                
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(Utils.color(cats.getString(key + ".name"), true));
                
                List<String> lore = new ArrayList<>();
                for (String l : cats.getStringList(key + ".lore")) lore.add(Utils.color(l, true));
                lore.add(""); 
                lore.add(ChatColor.BLACK + "cat:" + key);
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(slot, item);
            }
        }
        player.openInventory(inv);
    }

    public void openCategory(Player player, String categoryKey) {
        String title = Utils.color(plugin.getConfig().getString("shops." + categoryKey + ".title", "Shop"), true);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shops." + categoryKey + ".items");
        if (items != null) {
            int slot = 0;
            for (String itemKey : items.getKeys(false)) {
                Material mat = Material.matchMaterial(items.getString(itemKey + ".material", "DIRT"));
                if (mat == null) continue;
                double price = items.getDouble(itemKey + ".price");

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(Utils.color("&e&l" + mat.name().replace("_", " "), true));
                
                List<String> lore = new ArrayList<>();
                lore.add(Utils.color("&7Price per item: &a$" + String.format("%.2f", price), true));
                lore.add("");
                lore.add(Utils.color("&eClick to choose amount!", true));
                lore.add(ChatColor.BLACK + "buy:" + categoryKey + ":" + itemKey);
                meta.setLore(lore);
                item.setItemMeta(meta);
                
                inv.setItem(slot++, item);
            }
        }
        
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(Utils.color("&cBack to Categories", true));
        back.setItemMeta(backMeta);
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    private void playSound(Player p, String type) {
        try {
            Sound s = Sound.valueOf(plugin.getConfig().getString("sounds." + type));
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains(Utils.sc("Shop")) || event.getView().getTitle().contains(Utils.sc("Categories"))) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            if (!event.getCurrentItem().hasItemMeta() || !event.getCurrentItem().getItemMeta().hasLore()) return;

            Player p = (Player) event.getWhoClicked();
            List<String> lore = event.getCurrentItem().getItemMeta().getLore();
            String hiddenTag = lore.get(lore.size() - 1);

            playSound(p, "click");

            if (event.getCurrentItem().getType() == Material.ARROW) {
                openMainMenu(p);
                return;
            }

            if (hiddenTag.startsWith(ChatColor.BLACK + "cat:")) {
                openCategory(p, hiddenTag.split(":")[1]);
            } else if (hiddenTag.startsWith(ChatColor.BLACK + "buy:")) {
                String[] parts = hiddenTag.replace(ChatColor.BLACK.toString(), "").split(":");
                // Открываем BuyGUI (Передаем категорию, ключ предмета и стартовое количество 1)
                plugin.getBuyGUI().openBuyMenu(p, parts[1], parts[2], 1);
            }
        }
    }
}