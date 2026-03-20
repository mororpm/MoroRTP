package net.morosmp.rtp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    // Cache the stripped title so we only compute it once per GUI open, not on
    // every click event. This is also the key to the robust title comparison fix.
    private String cachedStrippedTitle = null;

    public RTPGUI(MoroRTP plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Small-caps converter (kept local so RTPGUI has no external dependencies)
    // -------------------------------------------------------------------------
    public String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small  = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true; sb.append(c); continue; }
            if (skip)     { sb.append(c); skip = false; continue; }
            int idx = normal.indexOf(c);
            sb.append(idx != -1 ? small.charAt(idx) : c);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // openMenu — builds and shows the inventory to the player
    // -------------------------------------------------------------------------
    public void openMenu(Player player) {
        int size  = plugin.getConfig().getInt("gui.size", 27);
        // Apply small-caps to the raw config title (§-codes already present)
        String title = sc(plugin.getConfig().getString("gui.title", "§5Moro Random Teleport"));

        // Cache the ChatColor-stripped version for use in the click handler.
        // ChatColor.stripColor removes all §x sequences so the comparison is
        // immune to formatting differences between what we set and what Bukkit
        // returns from InventoryView#getTitle().
        cachedStrippedTitle = ChatColor.stripColor(title);

        Inventory inv = Bukkit.createInventory(null, size, title);

        inv.setItem(plugin.getConfig().getInt("gui.world.slot",  11), getItem("gui.world"));
        inv.setItem(plugin.getConfig().getInt("gui.nether.slot", 13), getItem("gui.nether"));
        inv.setItem(plugin.getConfig().getInt("gui.end.slot",    15), getItem("gui.end"));

        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Click handler — robust title check using stripped color codes
    // -------------------------------------------------------------------------
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // FIX: Never compare raw titles directly. Bukkit may alter color-code
        // representation internally, causing strict equality checks to fail even
        // when the GUI is the correct one. Strip color from both sides first.
        String viewTitle = ChatColor.stripColor(event.getView().getTitle());

        // Lazy-init fallback in case openMenu was never called (shouldn't happen,
        // but defensive programming keeps the plugin from NPE-crashing on boot).
        if (cachedStrippedTitle == null) {
            cachedStrippedTitle = ChatColor.stripColor(
                sc(plugin.getConfig().getString("gui.title", "§5Moro Random Teleport"))
            );
        }

        if (!viewTitle.equals(cachedStrippedTitle)) return;

        // Cancel ALL clicks inside our GUI (prevents item duplication exploits)
        event.setCancelled(true);

        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.AIR) return;

        if (!(event.getWhoClicked() instanceof Player p)) return;

        int slot       = event.getRawSlot();
        int worldSlot  = plugin.getConfig().getInt("gui.world.slot",  11);
        int netherSlot = plugin.getConfig().getInt("gui.nether.slot", 13);
        int endSlot    = plugin.getConfig().getInt("gui.end.slot",    15);

        if (slot == worldSlot || slot == netherSlot || slot == endSlot) {
            playClickSound(p);
            p.closeInventory();

            if      (slot == worldSlot)  plugin.getTeleportManager().startTeleport(p, "world");
            else if (slot == netherSlot) plugin.getTeleportManager().startTeleport(p, "world_nether");
            else                         plugin.getTeleportManager().startTeleport(p, "world_the_end");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private ItemStack getItem(String path) {
        Material mat = Material.matchMaterial(
                plugin.getConfig().getString(path + ".material", "DIRT"));
        if (mat == null) mat = Material.DIRT;

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            // sc() converts the colored name to small-caps glyphs
            meta.setDisplayName(sc(plugin.getConfig().getString(path + ".name", "???")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void playClickSound(Player p) {
        try {
            Sound s = Sound.valueOf(
                    plugin.getConfig().getString("sounds.click", "UI_BUTTON_CLICK"));
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }
}
