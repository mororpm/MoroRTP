package net.morosmp.rtp;

import net.md_5.bungee.api.ChatColor;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RTPGUI — world-selection chest GUI for MoroRTP.
 *
 * V5 changes — HEX colour support:
 *   Added static {@link #translateHex(String)} which uses Bungee's
 *   {@link ChatColor#of(String)} to convert {@code &#RRGGBB} codes into the
 *   §x§R§R§G§G§B§B legacy format. Without this, config values like
 *   {@code &#DDEFE4OVERWORLD} were displayed as white text because Bukkit's
 *   {@code translateAlternateColorCodes} does not understand {@code &#…} syntax.
 *
 *   {@code translateHex()} is now applied to:
 *     - The inventory title  (gui.title)
 *     - Every item display name  (gui.world.name, gui.nether.name, gui.end.name)
 *
 *   The GUI singleton pattern, robust title comparison, and sc() converter are
 *   all preserved from v4.
 */
public class RTPGUI implements Listener {

    // Matches &#RRGGBB — exactly 6 hex digits, case-insensitive
    private static final Pattern HEX_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final MoroRTP plugin;

    // Cached stripped title for robust InventoryClickEvent matching.
    private String cachedStrippedTitle = null;

    public RTPGUI(MoroRTP plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Static HEX colour translator
    // =========================================================================

    /**
     * Translates {@code &#RRGGBB} codes and {@code &X} legacy codes into the
     * Minecraft §-colour format, using Bungee's {@link ChatColor#of(String)}
     * for the RGB conversion.
     *
     * <p>Apply this method to every string displayed in an inventory (names,
     * lore, title) so that hex colour codes from {@code config.yml} render as
     * true RGB rather than showing as plain white text.
     *
     * <p>Translation order:
     * <ol>
     *   <li>{@code &#RRGGBB} → {@code §x§R§R§G§G§B§B}  (via Bungee ChatColor.of)</li>
     *   <li>{@code &X}       → {@code §X}               (Bukkit translateAlternate)</li>
     * </ol>
     *
     * @param text raw string with {@code &#RRGGBB} and/or {@code &X} codes
     * @return fully translated legacy-colour string safe for item metadata
     */
    public static String translateHex(String text) {
        if (text == null || text.isEmpty()) return "";

        // Step 1: convert every &#RRGGBB occurrence using Bungee ChatColor.of()
        // ChatColor.of("#RRGGBB").toString() produces §x§R§R§G§G§B§B
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rgb  = "#" + matcher.group(1);           // e.g. "#DDEFE4"
            String legacy = ChatColor.of(rgb).toString();   // §x§D§D§E§F§E§4
            // quoteReplacement prevents $ and \ from being treated as regex tokens
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(legacy));
        }
        matcher.appendTail(buffer);

        // Step 2: translate remaining &X codes (§a, §c, §r, etc.)
        return org.bukkit.ChatColor
                .translateAlternateColorCodes('&', buffer.toString());
    }

    // =========================================================================
    // Small-caps converter
    // =========================================================================

    /** Converts ASCII letters to Small Caps unicode glyphs for the premium UI. */
    public String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small  = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true;  sb.append(c); continue; }
            if (skip)     { skip = false; sb.append(c); continue; }
            int idx = normal.indexOf(c);
            sb.append(idx != -1 ? small.charAt(idx) : c);
        }
        return sb.toString();
    }

    // =========================================================================
    // openMenu
    // =========================================================================

    public void openMenu(Player player) {
        int size = plugin.getConfig().getInt("gui.size", 27);

        // FIX: translateHex() must come BEFORE sc() so hex codes are resolved
        // to §-format before the small-caps pass (which ignores §-prefixed chars).
        String rawTitle = plugin.getConfig().getString(
                "gui.title", "&#E0D7FFMORO RANDOM TELEPORT");
        String title = sc(translateHex(rawTitle));

        // Cache stripped title for click handler matching.
        cachedStrippedTitle = org.bukkit.ChatColor.stripColor(title);

        Inventory inv = Bukkit.createInventory(null, size, title);
        inv.setItem(plugin.getConfig().getInt("gui.world.slot",  11), getItem("gui.world"));
        inv.setItem(plugin.getConfig().getInt("gui.nether.slot", 13), getItem("gui.nether"));
        inv.setItem(plugin.getConfig().getInt("gui.end.slot",    15), getItem("gui.end"));

        player.openInventory(inv);
    }

    // =========================================================================
    // Click handler
    // =========================================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String viewTitle = org.bukkit.ChatColor.stripColor(event.getView().getTitle());

        // Lazy-init safety: compute cached title if openMenu hasn't been called yet.
        if (cachedStrippedTitle == null) {
            String rawTitle = plugin.getConfig().getString(
                    "gui.title", "&#E0D7FFMORO RANDOM TELEPORT");
            cachedStrippedTitle = org.bukkit.ChatColor.stripColor(
                    sc(translateHex(rawTitle)));
        }

        if (!viewTitle.equals(cachedStrippedTitle)) return;

        event.setCancelled(true); // lock the GUI completely

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

    // =========================================================================
    // Item builder
    // =========================================================================

    /**
     * Reads material and display name from config and constructs an ItemStack.
     *
     * <p>FIX: {@link #translateHex(String)} is applied to the display name so
     * colour codes like {@code &#DDEFE4OVERWORLD} render as true RGB rather than
     * defaulting to white. {@link #sc(String)} converts letters to small caps.
     */
    private ItemStack getItem(String path) {
        Material mat = Material.matchMaterial(
                plugin.getConfig().getString(path + ".material", "DIRT"));
        if (mat == null) mat = Material.DIRT;

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            String rawName = plugin.getConfig().getString(path + ".name", "???");
            meta.setDisplayName(sc(translateHex(rawName)));
            item.setItemMeta(meta);
        }
        return item;
    }

    // =========================================================================
    // Sound helper
    // =========================================================================

    private void playClickSound(Player p) {
        try {
            Sound s = Sound.valueOf(
                    plugin.getConfig().getString("sounds.click", "UI_BUTTON_CLICK"));
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }
}
