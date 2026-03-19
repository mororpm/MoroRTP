package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * DeathListener — handles bounty claiming when a victim is killed.
 *
 * Priority is set to HIGH so we run AFTER combat plugins (which may cancel
 * or modify the death event) but still have a chance to drop our item.
 */
public class DeathListener implements Listener {

    private final MoroBounty    plugin;
    private final BountyManager manager;

    public DeathListener(MoroBounty plugin, BountyManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Quick exit if no bounty exists for this victim
        if (!manager.hasBounty(victim.getUniqueId())) return;

        Player killer = victim.getKiller();

        // --- Case 1: Killed by environment / no player killer ---
        // Bounty is canceled: reward is lost (item stays in YAML-deleted state).
        // This discourages suicide-to-deny-bounty strategies.
        if (killer == null) {
            String desc = manager.getBountyDescription(victim.getUniqueId());
            manager.cancelBounty(victim.getUniqueId());
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "[MoroBounty] "
                    + ChatColor.WHITE + "The bounty on " + ChatColor.RED + victim.getName()
                    + ChatColor.WHITE + " (" + (desc != null ? desc : "?") + ") was burned — no player killer.");
            return;
        }

        // --- Case 2: Self-kill (fall, /kill, etc. with themselves as killer) ---
        // Treat same as no killer — cancel the bounty.
        if (killer.equals(victim)) {
            manager.cancelBounty(victim.getUniqueId());
            return;
        }

        // --- Case 3: Legitimate player kill — claim the bounty ---

        // Read the description BEFORE claiming (claim deletes the YAML entry)
        String desc = manager.getBountyDescription(victim.getUniqueId());

        // claimAndRemoveBounty() deserializes the item AND removes it from storage
        ItemStack reward = manager.claimAndRemoveBounty(victim.getUniqueId());

        if (reward == null) {
            // Deserialization failed — log it and bail out; do NOT crash the event
            plugin.getLogger().warning("Failed to deserialize bounty item for victim "
                    + victim.getName() + " (" + victim.getUniqueId() + "). Bounty entry removed.");
            return;
        }

        // DROP the physical item at the victim's death location.
        // dropItemNaturally() gives it a small random velocity so it lands on top
        // of the victim's other drops, just like a regular item drop.
        victim.getWorld().dropItemNaturally(victim.getLocation(), reward);

        // Broadcast
        Bukkit.broadcastMessage(
                ChatColor.GREEN + "[MoroBounty] " + ChatColor.WHITE
                + killer.getName() + " has claimed the bounty on "
                + ChatColor.RED + victim.getName() + ChatColor.WHITE + "!"
                + ChatColor.GOLD + " (" + (desc != null ? desc : reward.getAmount() + "x " + reward.getType().name()) + ")");
    }
}
