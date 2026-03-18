package net.morosmp.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {
    private final MoroRTP plugin;
    private final Map<UUID, BukkitRunnable> teleportTasks = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TeleportManager(MoroRTP plugin) { this.plugin = plugin; }

    public String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder(); boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true; sb.append(c); continue; }
            if (skip) { sb.append(c); skip = false; continue; }
            int idx = normal.indexOf(c);
            if (idx != -1) sb.append(small.charAt(idx)); else sb.append(c);
        } return sb.toString();
    }

    public boolean isTeleporting(Player player) { return teleportTasks.containsKey(player.getUniqueId()); }

    private void playSound(Player p, String configPath, String defaultSound) {
        try {
            Sound s = Sound.valueOf(plugin.getConfig().getString(configPath, defaultSound));
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    public void startTeleport(Player player, String worldName) {
        int cooldownSec = plugin.getConfig().getInt("teleport-cooldown", 300);
        int delaySec = plugin.getConfig().getInt("teleport-delay", 3);

        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) {
                player.sendMessage(sc("§cWait ") + timeLeft + sc("s before using RTP again!"));
                return;
            }
        }

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = delaySec;
            @Override
            public void run() {
                if (ticks <= 0) {
                    performRTP(player, worldName);
                    teleportTasks.remove(player.getUniqueId());
                    cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSec * 1000L));
                    this.cancel();
                    return;
                }
                player.sendMessage(sc("§eTeleporting in ") + ticks + sc("s. Do not move!"));
                playSound(player, "sounds.countdown", "BLOCK_NOTE_BLOCK_HAT");
                ticks--;
            }
        };

        teleportTasks.put(player.getUniqueId(), task);
        // Запускаем таймер, который тикает каждую секунду (20 тиков)
        task.runTaskTimer(plugin, 0L, 20L);
    }

    public void cancelTeleport(Player player) {
        if (teleportTasks.containsKey(player.getUniqueId())) {
            teleportTasks.get(player.getUniqueId()).cancel();
            teleportTasks.remove(player.getUniqueId());
            player.sendMessage(sc("§cTeleport cancelled!"));
        }
    }

    private void performRTP(Player p, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) { p.sendMessage(sc("§cWorld not found!")); return; }

        int minDist = plugin.getConfig().getInt("min-distance", 500);
        int maxDist = plugin.getConfig().getInt("max-distance", 10000);
        int offset = plugin.getConfig().getInt("border-offset", 15);
        
        double borderSize = world.getWorldBorder().getSize() / 2.0;
        int borderMax = (int) borderSize - offset;

        // Лимитируем максимальную дистанцию границей мира
        int max = Math.min(maxDist, borderMax);
        if (max <= minDist) max = minDist + 1000;

        // Генерация с учетом минимальной дистанции
        int range = max - minDist;
        int x = (int) (Math.random() * range) + minDist;
        int z = (int) (Math.random() * range) + minDist;

        // Случайно делаем координаты отрицательными (4 сектора карты)
        if (Math.random() > 0.5) x = -x;
        if (Math.random() > 0.5) z = -z;

        // Скейл Незера
        if (world.getEnvironment() == World.Environment.NETHER && plugin.getConfig().getBoolean("scale-nether-coords", true)) {
            x /= 8;
            z /= 8;
        }

        int y;
        if (world.getEnvironment() == World.Environment.NETHER) {
            y = plugin.getConfig().getInt("nether-y-level", 50);
        } else {
            y = world.getHighestBlockYAt(x, z) + 1;
        }

        p.teleport(new Location(world, x + 0.5, y, z + 0.5));
        playSound(p, "sounds.success", "ENTITY_ENDERMAN_TELEPORT");
        p.sendMessage(sc("§aTeleported to X: " + x + " Z: " + z + " !"));
    }
}