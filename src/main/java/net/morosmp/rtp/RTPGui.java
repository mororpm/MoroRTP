package net.morosmp.rtp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class RTPGui implements InventoryHolder {
    private final MoroRTP plugin;
    private Inventory inv;

    public RTPGui(MoroRTP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        int rows = plugin.getConfig().getInt("gui.rows", 3);
        String titleStr = plugin.getConfig().getString("gui.title", "MoroRTP");
        Component title = plugin.getConfigManager().parseRawMessage(titleStr);
        
        inv = Bukkit.createInventory(this, rows * 9, title);
        
        boolean fillEmpty = plugin.getConfig().getBoolean("gui.fill-empty", true);
        if (fillEmpty) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
                String paneName = plugin.getConfig().getString("gui.empty-pane-name", " ");
                meta.displayName(plugin.getConfigManager().parseRawMessage(paneName));
                pane.setItemMeta(meta);
            }
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, pane);
            }
        }

        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("gui.items");
        if (itemsSection != null) {
            for (String slotStr : itemsSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotStr);
                    if (slot < 0 || slot >= inv.getSize()) continue;
                    
                    String path = "gui.items." + slotStr;
                    String matStr = plugin.getConfig().getString(path + ".material", "DIRT");
                    Material mat = Material.matchMaterial(matStr);
                    if (mat == null) mat = Material.DIRT;

                    ItemStack item = new ItemStack(mat);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        String name = plugin.getConfig().getString(path + ".name", "RTP");
                        meta.displayName(plugin.getConfigManager().parseRawMessage(name));
                        
                        List<String> loreStr = plugin.getConfig().getStringList(path + ".lore");
                        List<Component> lore = new ArrayList<>();
                        for (String l : loreStr) {
                            lore.add(plugin.getConfigManager().parseRawMessage(l));
                        }
                        meta.lore(lore);
                        
                        double price = plugin.getConfig().getDouble(path + ".price", 0.0);
                        int radius = plugin.getConfig().getInt(path + ".radius", 0);
                        
                        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rtp_price"), PersistentDataType.DOUBLE, price);
                        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rtp_radius"), PersistentDataType.INTEGER, radius);
                        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rtp_item"), PersistentDataType.BYTE, (byte) 1);
                        
                        item.setItemMeta(meta);
                    }
                    inv.setItem(slot, item);
                } catch (NumberFormatException ignored) {}
            }
        }
        
        player.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
