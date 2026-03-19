package net.morosmp.settings;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * SettingsGUI — 9-slot chest GUI layout:
 *
 *  [ GL ][ GL ][ PM  ][ GL ][ GL ][ GL ][ SND ][ GL ][ GL ]
 *    0     1    [2]    3     4     5    [6]    7     8
 *
 *  Slot 2 → PM toggle   (Lime/Gray Dye)
 *  Slot 6 → Kill sounds (Note Block / Barrier)
 *  Rest   → Gray glass panes
 */
public class SettingsGUI implements Listener {

    private static final String TITLE      = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "MoroSettings";
    private static final int    SLOT_PM    = 2;
    private static final int    SLOT_SOUND = 6;

    private final SettingsPlugin  plugin;
    private final SettingsManager manager;

    public SettingsGUI(SettingsPlugin plugin, SettingsManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    /** Builds and opens the settings inventory for the given player. */
    public static void open(Player player, SettingsManager manager) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);

        ItemStack glass = make(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            if (i != SLOT_PM && i != SLOT_SOUND) inv.setItem(i, glass);
        }

        UUID   uuid     = player.getUniqueId();
        boolean pmOn    = manager.isPmEnabled(uuid);
        boolean soundOn = manager.isKillSoundsEnabled(uuid);

        // PM toggle
        inv.setItem(SLOT_PM, make(
            pmOn ? Material.LIME_DYE : Material.GRAY_DYE,
            (pmOn ? "\u00a7a" : "\u00a7c") + "Private Messages: " + (pmOn ? "ON" : "OFF"),
            "\u00a77Left-click to toggle.",
            "\u00a78Controls /msg and /r."
        ));

        // Kill sounds toggle
        inv.setItem(SLOT_SOUND, make(
            soundOn ? Material.NOTE_BLOCK : Material.BARRIER,
            (soundOn ? "\u00a7a" : "\u00a7c") + "Kill Sounds: " + (soundOn ? "ON" : "OFF"),
            "\u00a77Left-click to toggle.",
            "\u00a78Affects kill sound effects.",
            "\u00a78(Read by MoroKillSound if installed)"
        ));

        player.openInventory(inv);
    }

    // ── Events ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isOurGUI(event.getView().getTitle())) return;

        event.setCancelled(true); // read-only GUI

        int slot = event.getRawSlot();
        if (slot == SLOT_PM) {
            manager.togglePm(player.getUniqueId());
            // Re-open on next tick: cannot open inventory inside click event
            Bukkit.getScheduler().runTask(plugin, () -> open(player, manager));
        } else if (slot == SLOT_SOUND) {
            manager.toggleKillSounds(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> open(player, manager));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!isOurGUI(event.getView().getTitle())) return;
        event.setCancelled(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private boolean isOurGUI(String title) {
        return ChatColor.stripColor(title).equals(ChatColor.stripColor(TITLE));
    }

    private static ItemStack make(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }
}
