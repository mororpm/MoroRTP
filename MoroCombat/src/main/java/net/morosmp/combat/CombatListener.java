package net.morosmp.combat;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class CombatListener implements Listener {

    private final MoroCombat plugin;
    private final CombatManager manager;

    public CombatListener(MoroCombat plugin, CombatManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // -------------------------------------------------------------------------
    // PvP tagging — keeps both players in combat
    // -------------------------------------------------------------------------
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity()  instanceof Player victim
         && event.getDamager() instanceof Player attacker) {
            if (victim.equals(attacker)) return;
            // tagPlayer() already tags BOTH sides inside CombatManager
            manager.tagPlayer(victim, attacker);
        }
    }

    // -------------------------------------------------------------------------
    // Combat-log handler
    // -------------------------------------------------------------------------
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (manager.isInCombat(player)) {

            // --- Effects first (lightning / broadcast) while the player object
            //     is still fully valid ---
            if (plugin.getConfig().getBoolean("effects.lightning-on-quit", true)) {
                player.getWorld().strikeLightningEffect(player.getLocation());
            }
            if (plugin.getConfig().getBoolean("effects.broadcast-on-quit", true)) {
                String msg = plugin.getConfig()
                        .getString("messages.quit-broadcast", "§c☠ §e%player% §ccombat logged!")
                        .replace("%player%", player.getName());
                Bukkit.broadcastMessage(manager.sc(msg));
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
            }

            // --- Kill the player to force physical item drops (Hardcore Lifesteal) ---
            // If their last attacker is still online we attribute the kill to them
            // so Minecraft fires a proper PlayerDeathEvent with a killer reference,
            // which lets MoroBounty / MoroStats process the kill correctly.
            UUID attackerId = manager.getLastAttacker(player);
            Player attacker  = attackerId != null ? Bukkit.getPlayer(attackerId) : null;

            if (attacker != null && attacker.isOnline()) {
                // Attributed kill — drops loot and credits the attacker
                player.damage(100_000.0, attacker);
            } else {
                // Unattributed kill — still forces item drops
                player.setHealth(0.0);
            }

            // FIX: Immediately wipe the UUID from the combat map AFTER killing them.
            //
            // Without this call the entry stays in combatMap forever. When the player
            // reconnects, isInCombat() still returns true (the timer hasn't expired),
            // meaning they are in combat the instant they log back in — without having
            // attacked or been attacked by anyone.  Calling removeCombat() here resets
            // their state so they start the next session clean.
            manager.removeCombat(player);
        }
    }

    // -------------------------------------------------------------------------
    // Block teleport commands while in combat
    // -------------------------------------------------------------------------
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInCombat(player)) return;

        String cmd = event.getMessage().toLowerCase().split(" ")[0];
        for (String blocked : plugin.getConfig().getStringList("blocked-commands")) {
            if (cmd.equals(blocked.toLowerCase())) {
                event.setCancelled(true);
                player.sendMessage(manager.sc(
                        plugin.getConfig().getString("messages.command-blocked",
                                "§cYou cannot use this command while in combat!")));
                return;
            }
        }
    }
}
