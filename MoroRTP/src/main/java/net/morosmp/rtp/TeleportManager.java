package net.morosmp.rtp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TeleportManager — manages RTP timer and player teleportation.
 *
 * V2 Changes (ActionBar countdown):
 * - Removed all countdown player.sendMessage().
 * - Every timer second -> sendActionBar("§aTeleporting in §l%d §as.")
 * - On success     -> sendActionBar("§aSuccess!") + one chat message with coords.
 * - On cancel      -> sendActionBar("§cYou moved! Teleportation cancelled.")
 * - Cooldown warning remains in chat (one-time message, not a countdown).
 */
public class TeleportManager {

    private final MoroRTP plugin;
    private final Map<UUID, BukkitRunnable> teleportTasks = new HashMap<>();
    private final Map<UUID, Long>           cooldowns     = new HashMap<>();

    public TeleportManager(MoroRTP plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Small-caps converter
    // -------------------------------------------------------------------------
    public String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small  = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true; sb.append(c); continue; }
            if (skip)     { sb.append(c); skip = false; continue; }
            int idx = normal.indexOf(c);
            sb.append(idx != -1 ? small.charAt(idx) : c);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Utility: Send ActionBar using Bungee API
    // -------------------------------------------------------------------------
    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------
    public boolean isTeleporting(Player player) {
        return teleportTasks.containsKey(player.getUniqueId());
    }

    private void playSound(Player p, String configPath, String defaultSound) {
        try {
            Sound s = Sound.valueOf(plugin.getConfig().getString(configPath, defaultSound));
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Start Teleport Timer
    // -------------------------------------------------------------------------
    public void startTeleport(Player player, String worldName) {
        int cooldownSec = plugin.getConfig().getInt("teleport-cooldown", 300);
        int delaySec    = plugin.getConfig().getInt("teleport-delay", 3);

        // --- Cooldown check (remains in chat as a single warning) ---
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) {
                player.sendMessage(sc("§cPlease wait " + timeLeft + " sec before your next RTP!"));
                return;
            }
        }

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = delaySec;

            @Override
            public void run() {
                // Protection: player might have logged off
                if (!player.isOnline()) {
                    teleportTasks.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }

                if (ticks <= 0) {
                    // --- Teleport ---
                    performRTP(player, worldName);
                    teleportTasks.remove(player.getUniqueId());
                    cooldowns.put(player.getUniqueId(),
                            System.currentTimeMillis() + (cooldownSec * 1000L));
                    this.cancel();
                    return;
                }

                // --- ActionBar countdown ---
                sendActionBar(player, "§aTeleporting in §l" + ticks + " §as.");
                playSound(player, "sounds.countdown", "BLOCK_NOTE_BLOCK_HAT");
                ticks--;
            }
        };

        teleportTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    // -------------------------------------------------------------------------
    // Cancel Teleport (Movement/Damage - called from RTPListener)
    // -------------------------------------------------------------------------
    public void cancelTeleport(Player player) {
        if (!teleportTasks.containsKey(player.getUniqueId())) return;

        teleportTasks.get(player.getUniqueId()).cancel();
        teleportTasks.remove(player.getUniqueId());

        sendActionBar(player, "§cYou moved! Teleportation cancelled.");
    }

    // -------------------------------------------------------------------------
    // Perform Teleportation
    // -------------------------------------------------------------------------
    private void performRTP(Player p, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            p.sendMessage(sc("§cWorld not found: " + worldName));
            return;
        }

        int minDist = plugin.getConfig().getInt("min-distance", 500);
        int maxDist = plugin.getConfig().getInt("max-distance", 10000);
        int offset  = plugin.getConfig().getInt("border-offset", 15);

        double borderSize = world.getWorldBorder().getSize() / 2.0;
        int    borderMax  = (int) borderSize - offset;

        int max = Math.min(maxDist, borderMax);
        if (max <= minDist) max = minDist + 1000;

        int range = max - minDist;
        int x = (int) (Math.random() * range) + minDist;
        int z = (int) (Math.random() * range) + minDist;

        if (Math.random() > 0.5) x = -x;
        if (Math.random() > 0.5) z = -z;

        // Nether coordinate scaling
        if (world.getEnvironment() == World.Environment.NETHER
                && plugin.getConfig().getBoolean("scale-nether-coords", true)) {
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

        sendActionBar(p, "§aSuccess!");
        p.sendMessage(sc("§aYou have been teleported to: "
                + "§fX: §e" + x + " §fZ: §e" + z));
    }
}