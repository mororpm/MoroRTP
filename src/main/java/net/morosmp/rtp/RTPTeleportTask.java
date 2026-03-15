package net.morosmp.rtp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class RTPTeleportTask extends BukkitRunnable {

    private final MoroRTP plugin;
    private final TeleportManager manager;
    private final UUID playerUUID;
    private int timeLeft;
    private final double price;
    private final int maxRadius;

    public RTPTeleportTask(MoroRTP plugin, TeleportManager manager, Player player, int timeLeft, double price, int maxRadius) {
        this.plugin = plugin;
        this.manager = manager;
        this.playerUUID = player.getUniqueId();
        this.timeLeft = timeLeft;
        this.price = price;
        this.maxRadius = maxRadius;
    }

    @Override
    public void run() {
        Player player = Bukkit.getPlayer(playerUUID);

        if (player == null || !player.isOnline()) {
            manager.cancelTeleport(playerUUID);
            return;
        }

        if (timeLeft > 0) {
            if (plugin.getConfig().getBoolean("actionbar.enabled", true)) {
                String text = plugin.getConfig().getString("actionbar.text", "<gray>Телепортация через <blue>%time%</blue> сек...</gray>");
                player.sendActionBar(plugin.getConfigManager().parseRawMessage(text, "%time%", String.valueOf(timeLeft)));
            }
            
            Sound tickSound = plugin.getConfigManager().getSound("tick");
            if (tickSound != null) {
                player.playSound(player.getLocation(), tickSound, 
                    plugin.getConfigManager().getSoundVolume("tick"), 
                    plugin.getConfigManager().getSoundPitch("tick"));
            }

            // Particles
            Particle particle = plugin.getConfigManager().getParticle();
            int amount = plugin.getConfigManager().getParticleAmount();
            Location loc = player.getLocation().add(0, 1, 0); // Center of player
            player.getWorld().spawnParticle(particle, loc, amount, 0.5, 1.0, 0.5, 0.05);

            timeLeft--;
        } else {
            // Time is up
            if (plugin.getConfig().getBoolean("actionbar.enabled", true)) {
                player.sendActionBar(Component.empty());
            }
            
            if (!plugin.getConfig().getBoolean("settings.disable-chat-messages", false)) {
                player.sendMessage(plugin.getConfigManager().parseMessage("searching"));
            }
            
            manager.findLocationAndTeleport(player, false, price, maxRadius);
            manager.cancelTeleport(playerUUID);
        }
    }
}
