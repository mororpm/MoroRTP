package net.morosmp.rtp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RTPListener implements Listener {
    private final MoroRTP plugin;

    public RTPListener(MoroRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getTeleportManager().isTeleporting(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            
            if (to == null) return;
            
            // Проверка на изменение координат X, Y или Z (поворот головы разрешен)
            if (from.getBlockX() != to.getBlockX() || 
                from.getBlockY() != to.getBlockY() || 
                from.getBlockZ() != to.getBlockZ()) {
                
                plugin.getTeleportManager().cancelTeleportWithEvent(player, "moved");
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.getTeleportManager().isTeleporting(player.getUniqueId())) {
                plugin.getTeleportManager().cancelTeleportWithEvent(player, "damaged");
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // Отмена стандартной механики кроватей/якорей
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            player.sendMessage("§e[MoroRTP] У нас хардкорный сервер. Кровати и якоря здесь не работают для возрождения!");
        }
        
        // Спавним на спавне мира временно (чтобы не упал в пустоту, пока ищем)
        event.setRespawnLocation(player.getWorld().getSpawnLocation());
        
        // Запускаем асинхронный поиск с задержкой, чтобы игрок успел респавнуться
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage("§cПодбираем безопасную точку возрождения...");
            plugin.getTeleportManager().findLocationAndTeleport(player, true, 0.0, 0);
        }, 5L);
    }
}
