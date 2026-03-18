package net.morosmp.rtp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class RTPListener implements Listener {
    private final TeleportManager teleportManager;

    public RTPListener(TeleportManager teleportManager) {
        this.teleportManager = teleportManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (teleportManager.isTeleporting(player)) {
            if (event.getFrom().distance(event.getTo()) > 0.1) {
                teleportManager.cancelTeleport(player);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (teleportManager.isTeleporting(player)) {
                teleportManager.cancelTeleport(player);
            }
        }
    }
}