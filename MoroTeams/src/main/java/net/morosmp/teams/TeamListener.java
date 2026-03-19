package net.morosmp.teams;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Prevents friendly fire between team members. */
public class TeamListener implements Listener {

    private final TeamManager manager;

    public TeamListener(TeamManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity()  instanceof Player victim))   return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        if (manager.areTeammates(victim.getUniqueId(), attacker.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage("\u00a7c[Teams] Friendly fire is disabled!");
        }
    }
}
