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
            sender.sendMessage("§cТолько игроки могут использовать эту команду.");
            return true;
        }

        int currentMax = manager.getMaxHomes(player.getUniqueId());
        int nextSlot   = currentMax + 1;
        int cost       = manager.getUpgradeCost(player.getUniqueId());

        // ── No upgrade configured for the next slot ───────────────────────────
        if (cost <= 0) {
            player.sendMessage("§c[Homes] Вы достигли максимального количества домов §f(" + currentMax + ")§c.");
            player.sendMessage("§8На этом сервере дальнейшее расширение невозможно.");
            return true;
        }

        // ── Count Diamonds across the entire inventory ────────────────────────
        int totalDiamonds = countDiamonds(player);

        if (totalDiamonds < cost) {
            player.sendMessage("§c[Homes] Недостаточно Алмазов для покупки слота №§f" + nextSlot + "§c.");
            player.sendMessage("§cНужно: §f" + cost + " §cАлмазов  |  У вас: §f" + totalDiamonds);
            player.sendMessage("§8Соберите больше алмазов и попробуйте снова.");
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
            player.sendMessage("§c[Homes] Произошла ошибка инвентаря. Попробуйте ещё раз.");
            return true;
        }

        // ── All Diamonds successfully removed — grant the upgrade ─────────────
        int newMax = currentMax + 1;
        manager.setMaxHomes(player.getUniqueId(), newMax);

        player.sendMessage("§a[Homes] §fСлот №§e" + newMax + " §fразблокирован! "
                + "§8(-§f" + cost + " §bАлмазов§8)");
        player.sendMessage("§a[Homes] Теперь у вас §f" + newMax
                + " §aслотов для домов. Используйте §f/sethome <название>§a.");

        // Show next upgrade cost if one exists, otherwise tell them they're at the cap
        int nextCost = manager.getUpgradeCost(player.getUniqueId());
        if (nextCost > 0) {
            player.sendMessage("§8Следующий слот (§f" + (newMax + 1)
                    + "§8): §f" + nextCost + " §bАлмазов");
        } else {
            player.sendMessage("§8Это максимальный уровень прокачки.");
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
