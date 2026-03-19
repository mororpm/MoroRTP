package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BountyGUI — 9-slot chest-style inventory.
 *
 * Layout (slot indices 0-8):
 *
 *  [ GLASS ][ GLASS ][ GLASS ][ GLASS ][ DEPOSIT ][ GLASS ][ GLASS ][ CONFIRM ][ CANCEL ]
 *     0        1        2        3          4         5        6         7         8
 *
 *  Slot 4 → Deposit slot  : player places the item they want to use as the bounty reward.
 *  Slot 7 → Confirm button: locks in the deposit and writes the bounty to YAML.
 *  Slot 8 → Cancel button : closes without doing anything; returns deposited item.
 *
 * One BountyGUI instance is registered as a Listener (singleton in MoroBounty).
 * A Map tracks which player is targeting which victim so multiple GUIs can be
 * open simultaneously.
 */
public class BountyGUI implements Listener {

    // Slot constants — change these to rearrange the layout
    private static final int DEPOSIT_SLOT  = 4;
    private static final int CONFIRM_SLOT  = 7;
    private static final int CANCEL_SLOT   = 8;

    private static final String GUI_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Set Bounty — Deposit Item";

    private final MoroBounty   plugin;
    private final BountyManager manager;

    // viewer UUID → victim UUID (tracks who is setting a bounty on whom)
    private final Map<UUID, UUID> activeSessions = new HashMap<>();

    public BountyGUI(MoroBounty plugin, BountyManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // -------------------------------------------------------------------------
    // Open the GUI
    // -------------------------------------------------------------------------

    /**
     * Opens the bounty-creation GUI for {@code sponsor}, targeting {@code victim}.
     * Calling this when the sponsor already has an open session replaces the old one.
     */
    public void openFor(Player sponsor, Player victim) {
        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);

        // Fill decorative glass panes
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            if (i != DEPOSIT_SLOT && i != CONFIRM_SLOT && i != CANCEL_SLOT) {
                inv.setItem(i, glass);
            }
        }

        // Deposit slot — left intentionally empty so the player can place their item
        inv.setItem(DEPOSIT_SLOT, makeItem(Material.LIME_STAINED_GLASS_PANE,
                ChatColor.GREEN + "» Place your bounty item here «",
                ChatColor.GRAY + "Any item works — Diamonds, Netherite, etc."));

        // Confirm button
        inv.setItem(CONFIRM_SLOT, makeItem(Material.EMERALD_BLOCK,
                ChatColor.GREEN + "" + ChatColor.BOLD + "✔  CONFIRM",
                ChatColor.GRAY + "Places the bounty on " + ChatColor.RED + victim.getName(),
                ChatColor.GRAY + "The item in slot 5 will be removed from",
                ChatColor.GRAY + "your inventory and locked as the reward."));

        // Cancel button
        inv.setItem(CANCEL_SLOT, makeItem(Material.REDSTONE_BLOCK,
                ChatColor.RED + "" + ChatColor.BOLD + "✘  CANCEL",
                ChatColor.GRAY + "Closes the menu. Your item is returned."));

        activeSessions.put(sponsor.getUniqueId(), victim.getUniqueId());
        sponsor.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player sponsor)) return;
        if (!isOurGUI(event.getView().getTitle())) return;

        int slot = event.getRawSlot();

        // --- Slots that are NOT the deposit slot must be completely locked ---
        if (slot != DEPOSIT_SLOT) {
            event.setCancelled(true);
        }

        // Prevent shift-clicking items FROM player inventory INTO our GUI to
        // bypass the deposit-slot restriction
        if (slot >= 9 && event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        UUID victimUuid = activeSessions.get(sponsor.getUniqueId());
        if (victimUuid == null) return;

        // --- CONFIRM ---
        if (slot == CONFIRM_SLOT) {
            handleConfirm(sponsor, victimUuid, event.getView().getTopInventory());
            return;
        }

        // --- CANCEL ---
        if (slot == CANCEL_SLOT) {
            sponsor.closeInventory(); // onClose() will return any deposited item
        }
    }

    // -------------------------------------------------------------------------
    // Drag handler — prevent dragging into non-deposit slots
    // -------------------------------------------------------------------------

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!isOurGUI(event.getView().getTitle())) return;

        // If ANY of the dragged-into slots are inside our 9-slot GUI and are NOT
        // the deposit slot, cancel the entire drag.
        for (int slot : event.getRawSlots()) {
            if (slot < 9 && slot != DEPOSIT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Close handler — always return whatever is in the deposit slot
    // -------------------------------------------------------------------------

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player sponsor)) return;
        if (!isOurGUI(event.getView().getTitle())) return;

        activeSessions.remove(sponsor.getUniqueId());

        // Return the deposit-slot item to the player's inventory if they closed
        // without confirming (or if confirm already cleared the slot — safe no-op).
        ItemStack leftover = event.getInventory().getItem(DEPOSIT_SLOT);
        if (isRealItem(leftover)) {
            returnItem(sponsor, leftover);
            event.getInventory().setItem(DEPOSIT_SLOT, null);
        }
    }

    // -------------------------------------------------------------------------
    // Confirm logic
    // -------------------------------------------------------------------------

    private void handleConfirm(Player sponsor, UUID victimUuid, Inventory gui) {
        ItemStack deposited = gui.getItem(DEPOSIT_SLOT);

        // Validate: deposit slot must contain a real item, not a placeholder pane
        if (!isRealItem(deposited)) {
            sponsor.sendMessage(ChatColor.RED + "[MoroBounty] Place the item you want to use as the bounty reward in the center slot first!");
            return;
        }

        // Validate: sponsor must still have that item in their inventory.
        // This prevents a duplication exploit where the player quickly swaps the
        // item out of their inventory between placing it and clicking confirm.
        if (!sponsor.getInventory().containsAtLeast(deposited, deposited.getAmount())) {
            sponsor.sendMessage(ChatColor.RED + "[MoroBounty] You no longer have that item in your inventory!");
            gui.setItem(DEPOSIT_SLOT, null);
            return;
        }

        // Check if target already has a bounty
        if (manager.hasBounty(victimUuid)) {
            sponsor.sendMessage(ChatColor.RED + "[MoroBounty] That player already has an active bounty!");
            // Return the item and close
            returnItem(sponsor, deposited);
            gui.setItem(DEPOSIT_SLOT, null);
            sponsor.closeInventory();
            return;
        }

        // ---- All checks passed — commit the bounty ----

        // 1. Remove the item physically from the sponsor's inventory
        //    removeItem() handles stacks correctly (e.g. 10 diamonds from multiple slots)
        sponsor.getInventory().removeItem(deposited.clone());

        // 2. Clear the GUI deposit slot (item is now "locked in")
        gui.setItem(DEPOSIT_SLOT, null);

        // 3. Persist to bounties.yml
        manager.setBounty(sponsor.getUniqueId(), victimUuid, deposited.clone());

        // 4. Feedback
        String victimName = Bukkit.getOfflinePlayer(victimUuid).getName();
        if (victimName == null) victimName = victimUuid.toString();

        sponsor.sendMessage(ChatColor.GREEN + "[MoroBounty] " + ChatColor.WHITE
                + "Bounty placed on " + ChatColor.RED + victimName
                + ChatColor.WHITE + "! Reward: " + ChatColor.GOLD
                + deposited.getAmount() + "x " + deposited.getType().name());

        // 5. Close the GUI (onClose will find an empty deposit slot — no double-return)
        sponsor.closeInventory();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Checks if the inventory title belongs to our GUI (color-stripped comparison). */
    private boolean isOurGUI(String title) {
        return ChatColor.stripColor(title).equals(ChatColor.stripColor(GUI_TITLE));
    }

    /**
     * Returns true if the ItemStack is non-null, not AIR, and not one of the
     * decorative glass panes used as fillers.
     */
    private boolean isRealItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        // Reject decorative glass panes (they are our own placeholders)
        return item.getType() != Material.GRAY_STAINED_GLASS_PANE
            && item.getType() != Material.LIME_STAINED_GLASS_PANE;
    }

    /** Gives an item back to the player, dropping it at their feet if inventory is full. */
    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        overflow.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    /** Convenience builder for GUI display items. */
    private ItemStack makeItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }
}
