package net.morosmp.rtp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TeleportManager — manages RTP countdown, safe-location search, and async teleport.
 *
 * V4 Changes — cooldown fix + async teleport:
 *
 *   BUG (v3): cooldown was applied inside the BukkitRunnable when ticks hit zero,
 *   BEFORE knowing whether a safe location existed or whether the teleport succeeded.
 *   A player who got "no safe spot found" was still locked out by a cooldown.
 *
 *   FIX: cooldown is now applied STRICTLY inside the CompletableFuture thenAccept
 *   callback of player.teleportAsync(), and ONLY when the boolean `success` is true.
 *   No safe spot → no cooldown. Teleport rejected by Paper → no cooldown.
 *
 *   PERF: Replaced synchronous p.teleport() with p.teleportAsync().
 *   Paper pre-loads the destination chunk off the main thread before moving the
 *   player, eliminating the brief TPS drop on first-time chunk generation.
 *
 *   END: max-attempts default raised to 100 in config.yml.
 *   The End has large void areas where rejection rate per attempt is very high.
 */
public class TeleportManager {

    // ── HEX color regex ───────────────────────────────────────────────────────
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    // ── Color palette (&#RRGGBB format) ───────────────────────────────────────
    private static final String C_GREEN = "&#22c55e";
    private static final String C_RED   = "&#ef4444";
    private static final String C_MUTED = "&#9ca3af";
    private static final String C_WHITE = "&#f9fafb";
    private static final String C_GOLD  = "&#fbbf24";

    // ── Runtime state ─────────────────────────────────────────────────────────
    private final MoroRTP plugin;
    private final Map<UUID, BukkitRunnable> teleportTasks = new HashMap<>();
    private final Map<UUID, Long>           cooldowns     = new HashMap<>();

    public TeleportManager(MoroRTP plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // HEX color translation
    // =========================================================================

    /**
     * Converts {@code &#RRGGBB} hex codes and {@code &X} legacy codes to the
     * §-format understood by Bungee TextComponent (ActionBar) and Bukkit chat.
     * Must be called on every string before it is sent to a player.
     */
    private static String color(String s) {
        Matcher m = HEX.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) r.append('§').append(c);
            m.appendReplacement(sb, r.toString());
        }
        m.appendTail(sb);
        return sb.toString().replace('&', '§');
    }

    // =========================================================================
    // Small-caps converter
    // =========================================================================

    /** Replaces ASCII letters with Small Caps unicode equivalents. */
    public String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small  = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true;  sb.append(c); continue; }
            if (skip)     { skip = false; sb.append(c); continue; }
            int idx = normal.indexOf(c);
            sb.append(idx >= 0 ? small.charAt(idx) : c);
        }
        return sb.toString();
    }

    // =========================================================================
    // Message helpers
    // =========================================================================

    /** Sends ActionBar with HEX support. */
    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(color(text)));
    }

    /** Sends a chat message with HEX + small-caps applied. */
    private void msg(Player player, String text) {
        player.sendMessage(sc(color(text)));
    }

    public boolean isTeleporting(Player player) {
        return teleportTasks.containsKey(player.getUniqueId());
    }

    private void playSound(Player p, String path, String fallback) {
        try {
            p.playSound(p.getLocation(),
                    Sound.valueOf(plugin.getConfig().getString(path, fallback)),
                    1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Start Teleport Timer
    // =========================================================================

    public void startTeleport(Player player, String worldName) {
        int cooldownSec = plugin.getConfig().getInt("teleport-cooldown", 300);
        int delaySec    = plugin.getConfig().getInt("teleport-delay",    3);

        // ── Cooldown check — one-time chat warning, not a countdown ───────────
        if (cooldowns.containsKey(player.getUniqueId())) {
            long remaining =
                    (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (remaining > 0) {
                msg(player,
                        C_RED   + "ꜱᴛᴏᴘ · "
                        + C_MUTED + "Wait "
                        + C_WHITE + remaining + "s "
                        + C_MUTED + "before using RTP again.");
                return;
            }
        }

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = delaySec;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    teleportTasks.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }

                if (ticks <= 0) {
                    // ── FIX: cooldown is NOT applied here ────────────────────
                    // It is applied inside performRTP's teleportAsync callback
                    // only if the teleport actually succeeds.
                    teleportTasks.remove(player.getUniqueId());
                    this.cancel();
                    performRTP(player, worldName, cooldownSec);
                    return;
                }

                sendActionBar(player,
                        C_GREEN + "ᴛᴇʟᴇᴘᴏʀᴛɪɴɢ · "
                        + C_WHITE + ticks + "s");
                playSound(player, "sounds.countdown", "BLOCK_NOTE_BLOCK_HAT");
                ticks--;
            }
        };

        teleportTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    // =========================================================================
    // Cancel Teleport — called by RTPListener on move / damage
    // =========================================================================

    public void cancelTeleport(Player player) {
        if (!teleportTasks.containsKey(player.getUniqueId())) return;
        teleportTasks.get(player.getUniqueId()).cancel();
        teleportTasks.remove(player.getUniqueId());
        sendActionBar(player, C_RED + "ᴄᴀɴᴄᴇʟʟᴇᴅ · " + C_MUTED + "You moved.");
    }

    // =========================================================================
    // Perform RTP — find safe location, then teleport asynchronously
    // =========================================================================

    /**
     * Searches for a safe location on the main thread (block API requirement),
     * then calls {@link Player#teleportAsync} which pre-loads the destination
     * chunk off-thread before moving the player.
     *
     * <p>{@code cooldownSec} is passed from the timer so it is captured at the
     * moment the countdown started, insulating it from potential /reload changes.
     *
     * @param p           the player to teleport
     * @param worldName   the target world name
     * @param cooldownSec seconds of cooldown — applied ONLY on teleport success
     */
    private void performRTP(Player p, String worldName, int cooldownSec) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            msg(p, C_RED + "ᴇʀʀᴏʀ · " + C_MUTED + "World '" + worldName + "' not found.");
            return;
        }

        int minDist     = plugin.getConfig().getInt("min-distance",  500);
        int maxDist     = plugin.getConfig().getInt("max-distance",  10000);
        int borderOff   = plugin.getConfig().getInt("border-offset", 15);
        // Default 100 — handles The End where large void areas cause high rejection.
        int maxAttempts = plugin.getConfig().getInt("max-attempts",  100);

        double borderSize = world.getWorldBorder().getSize() / 2.0;
        int    borderMax  = (int) borderSize - borderOff;
        int    max        = Math.min(maxDist, borderMax);
        if (max <= minDist) max = minDist + 1000;

        boolean isNether    = world.getEnvironment() == World.Environment.NETHER;
        boolean scaleNether = plugin.getConfig().getBoolean("scale-nether-coords", true);

        // Build blacklist once for this search session.
        // Re-read from config so /reload takes effect without restart.
        Set<Material> blacklist = buildBlacklist();

        // ── Safe-location search loop (runs on the main thread) ───────────────
        int     attempt = 0, x = 0, y = 0, z = 0;
        boolean found   = false;

        while (attempt++ < maxAttempts) {
            int range = max - minDist;
            x = (int) (Math.random() * range) + minDist;
            z = (int) (Math.random() * range) + minDist;
            if (Math.random() > 0.5) x = -x;
            if (Math.random() > 0.5) z = -z;

            if (isNether && scaleNether) { x /= 8; z /= 8; }

            y = isNether
                    ? plugin.getConfig().getInt("nether-y-level", 50)
                    : world.getHighestBlockYAt(x, z) + 1;

            // Three-block check:
            //   surface (y-1) — must not be blacklisted
            //   feet    (y)   — must be air (player spawn block)
            //   head    (y+1) — must be air (no suffocation)
            Material surface = world.getBlockAt(x, y - 1, z).getType();
            Material feet    = world.getBlockAt(x, y,     z).getType();
            Material head    = world.getBlockAt(x, y + 1, z).getType();

            if (!blacklist.contains(surface)
                    && feet.isAir() && !blacklist.contains(feet)
                    && head.isAir() && !blacklist.contains(head)) {
                found = true;
                break;
            }

            plugin.getLogger().fine(
                    "[RTP] Rejected (" + x + "," + y + "," + z + ")"
                    + " surface=" + surface + " feet=" + feet + " head=" + head);
        }

        // ── No safe location — abort WITHOUT applying cooldown ────────────────
        if (!found) {
            msg(p,
                    C_RED + "ꜰᴀɪʟᴇᴅ · "
                    + C_MUTED + "No safe location found after "
                    + maxAttempts + " attempts. Try again.");
            plugin.getLogger().warning(
                    "[RTP] No safe spot for " + p.getName()
                    + " in '" + worldName + "' after " + maxAttempts
                    + " attempts. Raise max-attempts or review blacklisted-blocks.");
            return;
        }

        // ── Async teleport — chunk pre-loaded off the main thread ─────────────
        final int fx = x, fy = y, fz = z;
        p.teleportAsync(new Location(world, fx + 0.5, fy, fz + 0.5))
                .thenAccept(success ->
                        // Schedule ALL post-teleport Bukkit API calls back to the
                        // main server thread. The thenAccept callback runs on
                        // Paper's async teleport pool, which is NOT the main thread.
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!p.isOnline()) return;

                            if (success) {
                                // ── COOLDOWN APPLIED HERE AND ONLY HERE ───────
                                // This is the sole location where cooldown is set.
                                // Guaranteed to run only after a confirmed,
                                // successful teleport with a pre-loaded chunk.
                                cooldowns.put(
                                        p.getUniqueId(),
                                        System.currentTimeMillis() + (cooldownSec * 1000L));

                                playSound(p, "sounds.success", "ENTITY_ENDERMAN_TELEPORT");
                                sendActionBar(p, C_GREEN + "✦ ᴛᴇʟᴇᴘᴏʀᴛᴇᴅ");
                                msg(p,
                                        C_GREEN + "ᴛᴇʟᴇᴘᴏʀᴛᴇᴅ "
                                        + C_MUTED + "→ "
                                        + C_WHITE + "X: " + C_GOLD + fx
                                        + C_WHITE + "  Z: " + C_GOLD + fz);
                            } else {
                                // Paper rejected the teleport — no cooldown.
                                sendActionBar(p,
                                        C_RED + "ᴛᴇʟᴇᴘᴏʀᴛ ꜰᴀɪʟᴇᴅ · "
                                        + C_MUTED + "Please try again.");
                                plugin.getLogger().warning(
                                        "[RTP] teleportAsync returned false for "
                                        + p.getName() + " at ("
                                        + fx + "," + fy + "," + fz + ").");
                            }
                        })
                );
    }

    // =========================================================================
    // Build Material blacklist
    // =========================================================================

    private Set<Material> buildBlacklist() {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : plugin.getConfig().getStringList("blacklisted-blocks")) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                set.add(mat);
            } else {
                plugin.getLogger().warning(
                        "[RTP] Unknown material '" + name
                        + "' in blacklisted-blocks — skipped.");
            }
        }
        return set;
    }
}
