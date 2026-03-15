package net.morosmp.rtp;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class MenuListener implements Listener {
    private final MoroRTP plugin;

    public MenuListener(MoroRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof RTPGui) {
            event.setCancelled(true);
            
            if (event.getClickedInventory() == null) return;
            // Разрешаем кликать по своему инвентарю, чтобы избежать багов, но блокируем Shift-клики
            if (!event.getClickedInventory().equals(event.getInventory())) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
                return;
            }

            ItemStack item = event.getCurrentItem();
            if (item == null || item.getItemMeta() == null) return;

            ItemMeta meta = item.getItemMeta();
            NamespacedKey isRtpItemKey = new NamespacedKey(plugin, "rtp_item");

            if (meta.getPersistentDataContainer().has(isRtpItemKey, PersistentDataType.BYTE)) {
                Player player = (Player) event.getWhoClicked();
                
                double price = meta.getPersistentDataContainer().getOrDefault(
                    new NamespacedKey(plugin, "rtp_price"), PersistentDataType.DOUBLE, 0.0);
                int radius = meta.getPersistentDataContainer().getOrDefault(
                    new NamespacedKey(plugin, "rtp_radius"), PersistentDataType.INTEGER, 0);

                player.closeInventory();
                plugin.getTeleportManager().startWarmup(player, price, radius);
            }
        }
    }
}
