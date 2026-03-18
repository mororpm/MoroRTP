package net.morosmp.combat;
import org.bukkit.Bukkit; import org.bukkit.Sound; import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent; import org.bukkit.event.player.PlayerQuitEvent; import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import java.util.UUID;
public class CombatListener implements Listener {
    private final MoroCombat plugin; private final CombatManager manager;
    public CombatListener(MoroCombat plugin, CombatManager manager) { this.plugin = plugin; this.manager = manager; }
    @EventHandler public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            if (victim.equals(attacker)) return;
            manager.tagPlayer(victim, attacker);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.isInCombat(player)) {
            manager.removeCombat(player);
            UUID attackerId = manager.getLastAttacker(player);
            Player attacker = attackerId != null ? Bukkit.getPlayer(attackerId) : null;
            if (attacker != null && attacker.isOnline()) { player.damage(100000.0, attacker); } else { player.setHealth(0.0); }
            for (Player p : Bukkit.getOnlinePlayers()) { p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f); }
            if (plugin.getConfig().getBoolean("effects.lightning-on-quit", true)) player.getWorld().strikeLightningEffect(player.getLocation());
            if (plugin.getConfig().getBoolean("effects.broadcast-on-quit", true)) {
                String msg = plugin.getConfig().getString("messages.quit-broadcast").replace("%player%", player.getName());
                Bukkit.broadcastMessage(manager.sc(msg));
            }
        }
    }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (manager.isInCombat(player)) {
            String cmd = event.getMessage().toLowerCase().split(" ")[0];
            for (String blocked : plugin.getConfig().getStringList("blocked-commands")) {
                if (cmd.equals(blocked.toLowerCase())) {
                    event.setCancelled(true);
                    player.sendMessage(manager.sc(plugin.getConfig().getString("messages.command-blocked")));
                    return;
                }
            }
        }
    }
}