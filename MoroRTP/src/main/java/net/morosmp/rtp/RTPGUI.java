package net.morosmp.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RTPGUI implements Listener {
    private final MoroRTP plugin;

    public RTPGUI(MoroRTP plugin) { this.plugin = plugin; }

    public String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder(); boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true; sb.append(c); continue; }
            if (skip) { sb.append(c); skip = false; continue; }
            int idx = normal.indexOf(c);
            if (idx != -1) sb.append(small.charAt(idx)); else sb.append(c);
        } return sb.toString();
    }

    public void openMenu(Player player) {
        int size = plugin.getConfig().getInt("gui.size", 27);
        String title = plugin.getConfig().getString("gui.title", "§5§lMoro Random Teleport");
        Inventory inv = Bukkit.createInventory(null, size, sc(title));
        
        inv.setItem(plugin.getConfig().getInt("gui.world.slot", 11), getItem("gui.world"));
        inv.setItem(plugin.getConfig().getInt("gui.nether.slot", 13), getItem("gui.nether"));
        inv.setItem(plugin.getConfig().getInt("gui.end.slot", 15), getItem("gui.end"));
        
        player.openInventory(inv);
    }

    private ItemStack getItem(String path) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "DIRT"));
        if (mat == null) mat = Material.DIRT;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(sc(plugin.getConfig().getString(path + ".name", "Unknown")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void playClickSound(Player p) {
        try {
            Sound s = Sound.valueOf(plugin.getConfig().getString("sounds.click", "UI_BUTTON_CLICK"));
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = sc(plugin.getConfig().getString("gui.title", "Teleport"));
        if (!event.getView().getTitle().equals(title)) return;
        event.setCancelled(true);
        
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        Player p = (Player) event.getWhoClicked();
        
        int slot = event.getRawSlot();
        int worldSlot = plugin.getConfig().getInt("gui.world.slot", 11);
        int netherSlot = plugin.getConfig().getInt("gui.nether.slot", 13);
        int endSlot = plugin.getConfig().getInt("gui.end.slot", 15);

        if (slot == worldSlot || slot == netherSlot || slot == endSlot) {
            playClickSound(p);
            p.closeInventory();
            if (slot == worldSlot) plugin.getTeleportManager().startTeleport(p, "world");
            else if (slot == netherSlot) plugin.getTeleportManager().startTeleport(p, "world_nether");
            else plugin.getTeleportManager().startTeleport(p, "world_the_end");
        }
    }
}