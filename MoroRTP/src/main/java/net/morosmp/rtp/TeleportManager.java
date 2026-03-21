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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TeleportManager — countdown timer, safe-location search, and async teleport.
 *
 * V5 Changes — three critical fixes:
 *
 * FIX 1 — Safe-location logic (was broken for The End and Nether):
 *   - Surface block must now be {@code isSolid()} in addition to passing the
 *     blacklist check. This explicitly rejects VOID_AIR, AIR, CAVE_AIR, and
 *     any other non-solid block as a landing surface — regardless of whether
 *     they appear in the config blacklist.
 *   - A dedicated {@code NETHER_HAZARDS} set (LAVA, MAGMA_BLOCK, FIRE, SOUL_FIRE,
 *     LAVA) is checked against ALL three positions (surface, feet, head) for
 *     Nether worlds. The regular blacklist check only covers the surface; hazards
 *     can exist at feet/head level in the Nether even with a fixed Y level.
 *   - The loop already generates fresh random (X, Z) coordinates each iteration,
 *     so every rejection immediately tries a genuinely different location.
 *
 * FIX 2 — ActionBar clear-before-cancel:
 *   When {@link #cancelTeleport(Player)} fires, an empty ActionBar message is
 *   sent first to instantly erase the countdown. Without this, the last
 *   countdown value ("ᴛᴇʟᴇᴘᴏʀᴛɪɴɢ · 1s") lingers for ~3 seconds after
 *   cancellation because the Minecraft client keeps the previous ActionBar
 *   visible until it naturally fades.
 *
 * FIX 3 (preserved from V4) — Cooldown only on success:
 *   Cooldown is applied strictly inside the teleportAsync success callback.
 */
public class TeleportManager {

    // ── HEX color regex ───────────────────────────────────────────────────────
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    // ── Color palette ─────────────────────────────────────────────────────────
    private static final String C_GREEN = "&#22c55e";
    private static final String C_RED   = "&#ef4444";
    private static final String C_MUTED = "&#9ca3af";
    private static final String C_WHITE = "&#f9fafb";
    private static final String C_GOLD  = "&#fbbf24";

    // ── Nether-specific hazard set — checked at ALL block positions ───────────
    // These are lethal even at feet/head level inside a Nether column.
    // LAVA (source) and LAVA (flowing) are distinct materials in modern Paper.
    private static final Set<Material> NETHER_HAZARDS = EnumSet.of(
            Material.LAVA,
            Material.MAGMA_BLOCK,
            Material.FIRE,
            Material.SOUL_FIRE
    );

    // ── Runtime state ─────────────────────────────────────────────────────────
    private final MoroRTP plugin;
    private final Map<UUID, BukkitRunnable> teleportTasks = new HashMap<>();
    private final Map<UUID, Long>           cooldowns     = new HashMap<>();

    public TeleportManager(MoroRTP plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // HEX colour translation (used for ActionBar and chat messages)
    // =========================================================================

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

    /** Sends an ActionBar message with full HEX colour support. */
    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(color(text)));
    }

    /** Sends an empty ActionBar, instantly clearing any previous message. */
    private void clearActionBar(Player player) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(""));
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

        // Cooldown gate — single chat message, not a repeating countdown
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
                    // Cooldown is NOT set here — only in the teleportAsync callback.
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

    /**
     * Cancels the active teleport countdown for the given player.
     *
     * <p>FIX: {@link #clearActionBar(Player)} is called FIRST to immediately
     * erase the countdown text. Without this, the last "ᴛᴇʟᴇᴘᴏʀᴛɪɴɢ · Ns"
     * message lingers on-screen for ~3 seconds after cancellation because the
     * Minecraft client keeps the previous ActionBar visible until it fades.
     */
    public void cancelTeleport(Player player) {
        if (!teleportTasks.containsKey(player.getUniqueId())) return;

        teleportTasks.get(player.getUniqueId()).cancel();
        teleportTasks.remove(player.getUniqueId());

        // FIX: instantly erase the countdown actionbar BEFORE showing cancel message.
        clearActionBar(player);
        sendActionBar(player, C_RED + "ᴄᴀɴᴄᴇʟʟᴇᴅ · " + C_MUTED + "You moved.");
    }

    // =========================================================================
    // Perform RTP
    // =========================================================================

    /**
     * Finds a safe random location (main thread — block API requires it), then
     * hands off to {@link Player#teleportAsync} to pre-load the chunk off-thread.
     *
     * <p>FIX 1 — Strict safety checks:
     * <ul>
     *   <li><b>All worlds:</b> Surface block must be {@code isSolid()}.
     *       This rejects VOID_AIR, AIR, CAVE_AIR, and any other non-solid
     *       block as a landing surface without needing to enumerate them in the
     *       config blacklist.</li>
     *   <li><b>Nether:</b> {@link #NETHER_HAZARDS} (LAVA, MAGMA_BLOCK, FIRE,
     *       SOUL_FIRE) are checked at surface, feet, AND head positions.
     *       The regular blacklist only gates the surface; hazards can appear at
     *       feet/head level in Nether columns.</li>
     * </ul>
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
        int maxAttempts = plugin.getConfig().getInt("max-attempts",  100);

        double borderSize = world.getWorldBorder().getSize() / 2.0;
        int    borderMax  = (int) borderSize - borderOff;
        int    max        = Math.min(maxDist, borderMax);
        if (max <= minDist) max = minDist + 1000;

        boolean isNether    = world.getEnvironment() == World.Environment.NETHER;
        boolean scaleNether = plugin.getConfig().getBoolean("scale-nether-coords", true);

        // Config-driven blacklist (re-read so /reload takes effect without restart).
        Set<Material> blacklist = buildBlacklist();

        // ── Safe-location search (main thread, regenerates fresh X/Z each attempt) ─
        int     attempt = 0, x = 0, y = 0, z = 0;
        boolean found   = false;

        while (attempt++ < maxAttempts) {
            // Generate fresh random (X, Z) every single attempt.
            // This ensures every rejection tries a genuinely new location.
            int range = max - minDist;
            x = (int) (Math.random() * range) + minDist;
            z = (int) (Math.random() * range) + minDist;
            if (Math.random() > 0.5) x = -x;
            if (Math.random() > 0.5) z = -z;

            if (isNether && scaleNether) { x /= 8; z /= 8; }

            y = isNether
                    ? plugin.getConfig().getInt("nether-y-level", 50)
                    : world.getHighestBlockYAt(x, z) + 1;

            // Fetch the three relevant blocks
            Material surface = world.getBlockAt(x, y - 1, z).getType(); // landing block
            Material feet    = world.getBlockAt(x, y,     z).getType(); // player's feet
            Material head    = world.getBlockAt(x, y + 1, z).getType(); // player's head

            // ── Universal checks ───────────────────────────────────────────────
            // 1. Surface must be solid — rejects VOID_AIR, AIR, CAVE_AIR, water,
            //    lava source, etc. without needing them all in the config blacklist.
            //    This is the primary protection against void landings in The End.
            if (!surface.isSolid()) continue;

            // 2. Surface must not be in the config blacklist (MAGMA, CACTUS, etc.)
            if (blacklist.contains(surface)) continue;

            // 3. Feet block must be passable air (player spawns inside it).
            if (!feet.isAir() || blacklist.contains(feet)) continue;

            // 4. Head block must be passable air (no suffocation).
            if (!head.isAir() || blacklist.contains(head)) continue;

            // ── Nether-specific hazard checks ──────────────────────────────────
            // Lava pools, fire patches, and magma blocks can exist at any Y level
            // in the Nether. Check ALL three positions against the hazard set —
            // the blacklist only covers the surface.
            if (isNether) {
                if (NETHER_HAZARDS.contains(surface)
                        || NETHER_HAZARDS.contains(feet)
                        || NETHER_HAZARDS.contains(head)) {
                    plugin.getLogger().fine(
                            "[RTP] Nether hazard at ("
                            + x + "," + y + "," + z + ")"
                            + " surface=" + surface
                            + " feet=" + feet
                            + " head=" + head);
                    continue;
                }
            }

            found = true;
            break;
        }

        // ── No safe location found — abort, no cooldown ───────────────────────
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
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!p.isOnline()) return;

                            if (success) {
                                // COOLDOWN APPLIED HERE AND ONLY HERE (FIX from V4).
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
    // Build config-driven blacklist
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
