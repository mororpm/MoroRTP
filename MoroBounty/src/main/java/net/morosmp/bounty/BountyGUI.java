package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BountyGUI — controls three inventory types:
 *
 *  1. BROWSE GUI (54 slots, double chest)
 *     ┌─────────────────────────────────────────────┐
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │  ← Rows 0-4 (slots 0-44)
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │    9 heads per row = 45/page
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │
 *     │ ←BACK   GL  SEARCH GL  PAGE  GL  GL  GL →  │  ← Row 5 (slots 45-53): navigation
 *     └─────────────────────────────────────────────┘
 *     Slot 45 = ← Back      (only if page > 0)
 *     Slot 47 = [SEARCH]    (always)
 *     Slot 49 = Page X/Y    (always)
 *     Slot 53 = Next →      (only if next page exists)
 *
 *  2. DEPOSIT GUI (9 slots, single chest)
 *     Opened via /bounty create <player>. v2 logic preserved.
 *     Slot 4 = deposit, Slot 7 = confirm, Slot 8 = cancel.
 *
 *  3. ANVIL GUI (search)
 *     Opened by clicking [SEARCH] inside Browse GUI.
 *     Player enters a nickname -> clicks result (slot 2) or closes anvil ->
 *     Browse GUI reopens with the applied filter.
 *
 * Singleton: one BountyGUI instance is registered as Listener in MoroBounty.java.
 */
public class BountyGUI implements Listener {

    // ═══════════════════════════════════════════════════════════════════════════
    // Title constants (stripColor comparison keeps matching safe)
    // ═══════════════════════════════════════════════════════════════════════════

    static final String BROWSE_TITLE  = ChatColor.DARK_PURPLE + "☠ ACTIVE BOUNTIES";
    static final String DEPOSIT_TITLE = ChatColor.DARK_PURPLE + "PLACE BOUNTY";

    // ═══════════════════════════════════════════════════════════════════════════
    // Deposit GUI slots (9-slot)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int DEPOSIT_SLOT  = 4;
    private static final int CONFIRM_SLOT  = 7;
    private static final int CANCEL_SLOT   = 8;

    // ═══════════════════════════════════════════════════════════════════════════
    // Browse GUI slots (54-slot, navigation row 45-53)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int SLOT_PREV         = 45;
    private static final int SLOT_SEARCH       = 47;
    private static final int SLOT_PAGE_INFO    = 49;
    private static final int SLOT_NEXT         = 53;
    private static final int ITEMS_PER_PAGE    = 45;   // slots 0-44

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final MoroBounty    plugin;
    private final BountyManager manager;

    /** sponsor UUID -> victim UUID (Deposit GUI) */
    private final Map<UUID, UUID>    depositSessions = new HashMap<>();

    /** player UUID -> current page (Browse GUI) */
    private final Map<UUID, Integer> pageMap         = new HashMap<>();

    /** player UUID -> active search filter (null = no filter) */
    private final Map<UUID, String>  filterMap       = new HashMap<>();

    /** players currently using an anvil search session */
    private final Set<UUID>          searchSessions  = new HashSet<>();

    public BountyGUI(MoroBounty plugin, BountyManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC OPEN METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opens Browse GUI for player.
     *
     * @param player player opening GUI
     * @param page zero-based page index
     * @param filter name filter string (null or "" = show all)
     */
    public void openBrowse(Player player, int page, String filter) {
        // 1) Fetch all victims with active bounties
        List<UUID> victims = manager.getAllBountyVictimUuids();

        // 2) Apply search filter
        final boolean hasFilter = filter != null && !filter.isBlank();
        if (hasFilter) {
            final String lf = filter.toLowerCase();
            victims = victims.stream().filter(uuid -> {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                return op.getName() != null && op.getName().toLowerCase().contains(lf);
            }).collect(Collectors.toList());
        }

        // 3) Calculate page bounds
        int totalPages = Math.max(1, (int) Math.ceil(victims.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        // 4) Save navigation state
        pageMap.put(player.getUniqueId(), page);
        if (hasFilter) filterMap.put(player.getUniqueId(), filter);
        else           filterMap.remove(player.getUniqueId());

        // 5) Build inventory
        Inventory inv = Bukkit.createInventory(null, 54, BROWSE_TITLE);

        // Fill head slots (0-44)
        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, victims.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildHeadItem(victims.get(i)));
        }

        // Fill navigation row with glass
        ItemStack navGlass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < 54; s++) inv.setItem(s, navGlass);

        // <- Back
        if (page > 0) {
            inv.setItem(SLOT_PREV, makeItem(Material.ARROW,
                    ChatColor.YELLOW + "← BACK",
                    ChatColor.GRAY + "Page " + page + " of " + totalPages));
        }

        // [SEARCH]
        List<String> searchLore = new ArrayList<>();
        searchLore.add(ChatColor.GRAY + "Click to search by player name");
        if (hasFilter) {
            searchLore.add(ChatColor.YELLOW + "Active filter: " + ChatColor.WHITE + filter);
            searchLore.add(ChatColor.RED + "Right-click to clear filter");
        } else {
            searchLore.add(ChatColor.GRAY + "No filter applied");
        }
        inv.setItem(SLOT_SEARCH, makeItem(Material.BOOK,
                ChatColor.AQUA + "[SEARCH]",
                searchLore.toArray(new String[0])));

        // Page X of Y
        inv.setItem(SLOT_PAGE_INFO, makeItem(Material.PAPER,
                ChatColor.AQUA + "PAGE " + (page + 1) + " / " + totalPages,
                ChatColor.GRAY + "Total bounties: " + victims.size()));

        // Next ->
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeItem(Material.ARROW,
                    ChatColor.YELLOW + "NEXT →",
                    ChatColor.GRAY + "Page " + (page + 2) + " of " + totalPages));
        }

        player.openInventory(inv);
    }

    /**
     * Opens Deposit GUI for placing bounty on victim.
     * Called from BountyCommand (/bounty create <player>).
     */
    public void openFor(Player sponsor, Player victim) {
        Inventory inv = Bukkit.createInventory(null, 9, DEPOSIT_TITLE);

        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            if (i != DEPOSIT_SLOT && i != CONFIRM_SLOT && i != CANCEL_SLOT)
                inv.setItem(i, glass);
        }

        inv.setItem(DEPOSIT_SLOT, makeItem(Material.LIME_STAINED_GLASS_PANE,
                ChatColor.GREEN + "» DROP REWARD ITEM HERE «",
                ChatColor.GRAY + "Diamonds, Netherite, or any item"));

        inv.setItem(CONFIRM_SLOT, makeItem(Material.EMERALD_BLOCK,
                ChatColor.GREEN + "✔  CONFIRM",
                ChatColor.GRAY + "Place bounty on " + ChatColor.RED + victim.getName(),
                ChatColor.GRAY + "Item will be removed from your inventory"));

        inv.setItem(CANCEL_SLOT, makeItem(Material.REDSTONE_BLOCK,
                ChatColor.RED + "✘  CANCEL",
                ChatColor.GRAY + "Close menu. Item will be returned."));

        depositSessions.put(sponsor.getUniqueId(), victim.getUniqueId());
        sponsor.openInventory(inv);
    }

    /**
     * Opens Anvil GUI for search.
     * Called internally when [SEARCH] is clicked.
     */
    private void openSearch(Player player) {
        searchSessions.add(player.getUniqueId());

        // openAnvil(location, force=true) — Paper API, available since 1.14+
        InventoryView view = player.openAnvil(player.getLocation(), true);
        if (view == null) {
            searchSessions.remove(player.getUniqueId());
            return;
        }

        // Put paper in slot 0 as rename template (= text input seed)
        ItemStack seed = makeItem(Material.PAPER,
                ChatColor.GRAY + "Enter player name...",
                ChatColor.GRAY + "Type a name in the top field and",
                ChatColor.GRAY + "click the output item ▶");
        view.getTopInventory().setItem(0, seed);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── Browse GUI ────────────────────────────────────────────────────────
        if (isBrowseGUI(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            // Ignore clicks in player's bottom inventory while Browse GUI is open
            if (slot < 0 || slot >= 54) return;

            int     page   = pageMap.getOrDefault(player.getUniqueId(), 0);
            String  filter = filterMap.get(player.getUniqueId());

            switch (slot) {
                case SLOT_PREV -> openBrowse(player, page - 1, filter);

                case SLOT_NEXT -> openBrowse(player, page + 1, filter);

                case SLOT_SEARCH -> {
                    boolean isRightClick = event.isRightClick();
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (isRightClick && filter != null) {
                            // Right-click: clear filter and go back to page 0
                            openBrowse(player, 0, null);
                        } else {
                            // Left-click: open Anvil search
                            openSearch(player);
                        }
                    }, 1L);
                }

                case SLOT_PAGE_INFO -> { /* info slot, no action */ }

                default -> { /* head click: no action */ }
            }
            return;
        }

        // ── Deposit GUI ───────────────────────────────────────────────────────
        if (isDepositGUI(title)) {
            int slot = event.getRawSlot();

            // Block every slot except deposit slot
            if (slot != DEPOSIT_SLOT) event.setCancelled(true);

            // Block shift-click from player inventory into GUI (dupe protection)
            if (slot >= 9 && event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }

            UUID victimUuid = depositSessions.get(player.getUniqueId());
            if (victimUuid == null) return;

            if (slot == CONFIRM_SLOT) {
                handleConfirm(player, victimUuid, event.getView().getTopInventory());
            } else if (slot == CANCEL_SLOT) {
                player.closeInventory(); // onClose returns item
            }
            return;
        }

        // ── Anvil (search) ────────────────────────────────────────────────────
        if (searchSessions.contains(player.getUniqueId())
                && event.getInventory() instanceof AnvilInventory anvilInv) {

            // Slot 2 = Anvil output slot; confirms input
            if (event.getRawSlot() == 2) {
                event.setCancelled(true);
                String query = anvilInv.getRenameText();
                searchSessions.remove(player.getUniqueId());
                player.closeInventory();
                final String q = (query != null && !query.isBlank()) ? query.trim() : null;
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> openBrowse(player, 0, q), 1L);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();

        if (isBrowseGUI(title)) {
            event.setCancelled(true);
            return;
        }

        if (isDepositGUI(title)) {
            // In Deposit GUI allow dragging only into deposit slot
            for (int slot : event.getRawSlots()) {
                if (slot < 9 && slot != DEPOSIT_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── Deposit GUI closed — return item from deposit slot ────────────────
        if (isDepositGUI(title)) {
            depositSessions.remove(player.getUniqueId());
            ItemStack leftover = event.getInventory().getItem(DEPOSIT_SLOT);
            if (isRealItem(leftover)) {
                event.getInventory().setItem(DEPOSIT_SLOT, null);
                returnItem(player, leftover);
            }
            return;
        }

        // ── Browse GUI closed — keep pageMap/filterMap (resume on reopen) ─────

        // ── Anvil closed without confirm — still apply current query ──────────
        if (searchSessions.contains(player.getUniqueId())
                && event.getInventory() instanceof AnvilInventory anvilInv) {

            searchSessions.remove(player.getUniqueId());
            String query = anvilInv.getRenameText();

            // Do not apply default placeholder text as filter
            final boolean isPlaceholder = query != null
                    && ChatColor.stripColor(query).equalsIgnoreCase("Enter player name...");
            final String q = (!isPlaceholder && query != null && !query.isBlank())
                    ? query.trim() : null;

            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> openBrowse(player, 0, q), 1L);
        }
    }

    /**
     * PrepareAnvilEvent: makes search free (XP = 0) and always provides
     * an output item so the player can click to confirm.
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!searchSessions.contains(player.getUniqueId())) return;

        // Cost = 0 (no XP usage)
        event.getInventory().setRepairCost(0);

        // Build result item from slot 0 with current input text
        ItemStack base = event.getInventory().getItem(0);
        if (base == null) return;

        String renameText = event.getInventory().getRenameText();
        boolean hasQuery  = renameText != null && !renameText.isBlank();

        ItemStack result = base.clone();
        ItemMeta  meta   = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(hasQuery
                    ? ChatColor.AQUA + renameText
                    : ChatColor.GRAY + "Enter name...");
            meta.setLore(hasQuery
                    ? List.of(ChatColor.GREEN + "Click to search: " + ChatColor.WHITE + renameText)
                    : List.of(ChatColor.GRAY + "Type a name in the top field"));
            result.setItemMeta(meta);
        }
        event.setResult(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE: Deposit confirm
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleConfirm(Player sponsor, UUID victimUuid, Inventory gui) {
        ItemStack deposited = gui.getItem(DEPOSIT_SLOT);

        if (!isRealItem(deposited)) {
            sponsor.sendMessage(ChatColor.RED
                    + "[BOUNTY] Place a reward item in the center slot first!");
            return;
        }

        // Anti-dupe: ensure item still exists in player's inventory
        if (!sponsor.getInventory().containsAtLeast(deposited, deposited.getAmount())) {
            sponsor.sendMessage(ChatColor.RED + "[BOUNTY] You no longer have this item!");
            gui.setItem(DEPOSIT_SLOT, null);
            return;
        }

        if (manager.hasBounty(victimUuid)) {
            sponsor.sendMessage(ChatColor.RED
                    + "[BOUNTY] This player already has an active bounty!");
            returnItem(sponsor, deposited);
            gui.setItem(DEPOSIT_SLOT, null);
            sponsor.closeInventory();
            return;
        }

        // ── All checks passed — commit bounty ─────────────────────────────────
        sponsor.getInventory().removeItem(deposited.clone());
        gui.setItem(DEPOSIT_SLOT, null);
        manager.setBounty(sponsor.getUniqueId(), victimUuid, deposited.clone());

        String victimName = Bukkit.getOfflinePlayer(victimUuid).getName();
        if (victimName == null) victimName = victimUuid.toString().substring(0, 8) + "...";

        sponsor.sendMessage(ChatColor.GREEN + "[BOUNTY] " + ChatColor.WHITE
                + "Bounty placed on " + ChatColor.RED + victimName
                + ChatColor.WHITE + "! Reward: " + ChatColor.GOLD
                + deposited.getAmount() + "x " + deposited.getType().name());

        sponsor.closeInventory();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE: Item builders
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Builds victim head with full bounty info.
     * Lore: "§cHEAD PRICE: §f<amount> §b<item type>"
     */
    private ItemStack buildHeadItem(UUID victimUuid) {
        OfflinePlayer op   = Bukkit.getOfflinePlayer(victimUuid);
        String victimName  = op.getName() != null
                ? op.getName()
                : victimUuid.toString().substring(0, 8) + "...";

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        meta.setOwningPlayer(op);
        meta.setDisplayName(ChatColor.RED + "☠ " + victimName);

        // Build lore from deserialized reward item
        List<String> lore = new ArrayList<>();
        ItemStack rewardItem = manager.getBountyItem(victimUuid);
        if (rewardItem != null) {
            lore.add(ChatColor.RED + "HEAD PRICE: "
                    + ChatColor.WHITE + rewardItem.getAmount()
                    + " " + ChatColor.AQUA + prettyMaterial(rewardItem.getType().name()));

            // If item has custom display name, include it
            if (rewardItem.hasItemMeta() && rewardItem.getItemMeta().hasDisplayName()) {
                lore.add(ChatColor.GRAY + "(" + rewardItem.getItemMeta().getDisplayName()
                        + ChatColor.GRAY + ")");
            }
        } else {
            // Fallback to YAML text description (fast, no deserialization)
            String desc = manager.getBountyDescription(victimUuid);
            lore.add(ChatColor.RED + "HEAD PRICE: "
                    + ChatColor.AQUA + (desc != null ? desc : "???"));
        }
        lore.add(ChatColor.DARK_GRAY + "ID: " + victimUuid.toString().substring(0, 8) + "...");

        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    /** "DIAMOND_SWORD" -> "Diamond Sword" (readable lore rendering) */
    private String prettyMaterial(String raw) {
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private ItemStack makeItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE: Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Color-stripped comparison for robust title matching. */
    private boolean isBrowseGUI(String title) {
        return ChatColor.stripColor(title)
                .equals(ChatColor.stripColor(BROWSE_TITLE));
    }

    private boolean isDepositGUI(String title) {
        return ChatColor.stripColor(title)
                .equals(ChatColor.stripColor(DEPOSIT_TITLE));
    }

    /** true = real item, not a glass placeholder */
    private boolean isRealItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType() != Material.GRAY_STAINED_GLASS_PANE
            && item.getType() != Material.LIME_STAINED_GLASS_PANE;
    }

    /** Returns item to inventory; drops at feet if inventory is full. */
    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        overflow.values().forEach(l ->
                player.getWorld().dropItemNaturally(player.getLocation(), l));
    }
}
