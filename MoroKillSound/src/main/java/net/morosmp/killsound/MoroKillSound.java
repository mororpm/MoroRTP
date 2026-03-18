package net.morosmp.killsound;
import org.bukkit.Sound; import org.bukkit.entity.Player; import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.entity.PlayerDeathEvent; import org.bukkit.plugin.java.JavaPlugin;
public class MoroKillSound extends JavaPlugin implements Listener {
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
    @EventHandler public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            killer.playSound(killer.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            killer.playSound(killer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);
        }
    }
}