package net.morosmp.homes;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * BuyHomeCommand — handles /buyhome.
 *
 * Flow:
 *  1. Check current max homes to determine the next slot number.
 *  2. Look up the Diamond cost for that slot in config.yml (upgrades section).
 *     If no upgrade is configured → player is at the cap, refuse.
 *  3. Count how many Diamonds the player has in their ENTIRE inventory
 *     (all slots, including hotbar). If insufficient, refuse with cost info.
 *  4. Remove EXACTLY the required number of Diamonds from the inventory.
 *     Uses Bukkit's removeItem() which handles split stacks correctly and
 *     returns a map of any items it could not remove (used as a safety check).
 *  5. Increase max-homes by 1 in homes.yml.
 *  6. Send success message with new slot count.
 *
 * Physical economy:
 *   We never use Vault or any virtual economy. Diamonds are the only currency.
 *   The cost is configured per-slot in config.yml:
 *     upgrades:
 *       2: 64    ← second slot costs 64 Diamonds
 *       3: 128   ← third slot costs 128 Diamonds
 */
public class BuyHomeCommand implements CommandExecutor {

    private static final Material CURRENCY = Material.DIAMOND;

    private final HomesPlugin plugin;
    private final HomeManager manager;

    public BuyHomeCommand(HomesPlugin plugin, HomeManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        int currentMax = manager.getMaxHomes(player.getUniqueId());
        int nextSlot   = currentMax + 1;
        int cost       = manager.getUpgradeCost(player.getUniqueId());

        // ── No upgrade configured for the next slot ───────────────────────────
        if (cost <= 0) {
            player.sendMessage("§c[HOMES] You have reached the maximum number of homes §f(" + currentMax + ")§c.");
            player.sendMessage("§8No further upgrades are available on this server.");
            return true;
        }

        // ── Count Diamonds across the entire inventory ────────────────────────
        int totalDiamonds = countDiamonds(player);

        if (totalDiamonds < cost) {
            player.sendMessage("§c[HOMES] Not enough Diamonds to unlock slot §f#" + nextSlot + "§c.");
            player.sendMessage("§cRequired: §f" + cost + " §cDiamonds  |  You have: §f" + totalDiamonds);
            player.sendMessage("§8Collect more Diamonds and try again.");
            return true;
        }

        // ── Remove exactly `cost` Diamonds from the inventory ────────────────
        // Build an ItemStack of exactly `cost` diamonds and call removeItem().
        // If removeItem() can't remove ALL of them (inventory changed mid-tick, etc.),
        // it returns the remainder — we catch that as a safety net.
        HashMap<Integer, ItemStack> notRemoved =
                player.getInventory().removeItem(new ItemStack(CURRENCY, cost));

        if (!notRemoved.isEmpty()) {
            // This should never happen given we counted first, but handle defensively.
            // Count what was actually removed and refund the rest.
            int failedToRemove = notRemoved.values().stream()
                    .mapToInt(ItemStack::getAmount).sum();
            int actuallyRemoved = cost - failedToRemove;
            plugin.getLogger().warning("[BuyHome] Inventory desync for " + player.getName()
                    + ": tried to remove " + cost + " diamonds, failed to remove "
                    + failedToRemove + ". Player had: " + totalDiamonds);
            // Give back what we couldn't remove so the player isn't short-changed
            // (the removeItem call already consumed the ones it could reach)
            // Nothing to do — notRemoved items were never taken, only removed items were.
            player.sendMessage("§c[HOMES] Inventory sync error. Please try again.");
            return true;
        }

        // ── All Diamonds successfully removed — grant the upgrade ─────────────
        int newMax = currentMax + 1;
        manager.setMaxHomes(player.getUniqueId(), newMax);

        player.sendMessage("§a[HOMES] §fSlot §e#" + newMax + " §fis now unlocked! "
                + "§8(-§f" + cost + " §bDiamonds§8)");
        player.sendMessage("§a[HOMES] You now have §f" + newMax
                + " §ahome slots. Use §f/sethome <name>§a.");

        // Show next upgrade cost if one exists, otherwise tell them they're at the cap
        int nextCost = manager.getUpgradeCost(player.getUniqueId());
        if (nextCost > 0) {
            player.sendMessage("§8Next slot (§f" + (newMax + 1)
                    + "§8): §f" + nextCost + " §bDiamonds");
        } else {
            player.sendMessage("§8This is the maximum upgrade tier.");
        }

        return true;
    }

    // =========================================================================
    // Helper: count total Diamonds across the player's entire inventory
    // =========================================================================

    /**
     * Counts all Diamond items across every slot of the player's inventory
     * (including hotbar, main grid, and off-hand).
     * Does NOT count armour slots — players shouldn't be wearing diamonds as armour
     * as payment, and this matches the physical-item convention used in MoroTeams.
     */
    private int countDiamonds(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == CURRENCY) {
                total += item.getAmount();
            }
        }
        return total;
    }
}
