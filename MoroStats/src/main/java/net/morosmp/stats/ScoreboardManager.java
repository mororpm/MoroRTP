package net.morosmp.stats;

import fr.mrmicky.fastboard.FastBoard;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ScoreboardManager — manages a per-player {@link FastBoard} scoreboard.
 *
 * <h2>Why FastBoard?</h2>
 * The standard Bukkit {@code Scoreboard} API updates scores via packets that
 * the client processes one-by-one, causing a visible flicker on every refresh.
 * FastBoard builds and sends the {@code SET_DISPLAY_OBJECTIVE} and
 * {@code SET_SCORE} packets in a single batch using NMS reflection, eliminating
 * the flicker entirely.
 *
 * <h2>Thread safety</h2>
 * The update task runs <em>asynchronously</em> (off the main thread). FastBoard
 * is safe to call from any thread for line/title updates. The map itself uses
 * {@link ConcurrentHashMap} so join/quit events (main thread) and the update
 * task (async thread) can read/write simultaneously without locks.
 *
 * <h2>PAPI placeholders</h2>
 * PlaceholderAPI's {@code setPlaceholders()} is <strong>not</strong> async-safe
 * by default — but the Statistic expansion reads cached values, making it
 * effectively safe in practice. If you add expansions that do database I/O,
 * move them to their own sync task or use PAPI's async-compatible expansions.
 *
 * <h2>Layout</h2>
 * <pre>
 *  ┌─────────────────────────────┐
 *  │  ᴍ ᴏ ʀ ᴏ ꜱ ᴍ ᴘ           (title — cyan)
 *  │                              (empty)
 *  │  ᴘ ʀ ᴏ ꜰ ɪ ʟ ᴇ:            (section header — light gray)
 *  │  | Steve                    (dim gray pipe + white value)
 *  │  | Ping: 12                 (dim gray pipe + cyan label + white value)
 *  │                              (empty)
 *  │  ꜱ ᴛ ᴀ ᴛ ꜱ:               (section header)
 *  │  | Kills: 42                (green label)
 *  │  | Deaths: 7                (red label)
 *  │  | Time: 3h 20m             (purple label)
 *  │                              (empty)
 *  │  morosmp.net                (footer — green)
 *  └─────────────────────────────┘
 * </pre>
 */
public class ScoreboardManager {

    // ── Small-caps unicode strings for section headers ─────────────────────────
    // These are hard-coded unicode characters, NOT font tricks. They render in
    // every Minecraft version that supports Unicode without any resource pack.
    private static final String HEADER_PROFILE = "&#d1d5dbᴘ ʀ ᴏ ꜰ ɪ ʟ ᴇ:";
    private static final String HEADER_STATS   = "&#d1d5dbꜱ ᴛ ᴀ ᴛ ꜱ:";

    // ── Color palette (&#RRGGBB format, translated by ColorUtil) ──────────────
    private static final String CYAN         = "&#00EAFF";
    private static final String LIGHT_GRAY   = "&#d1d5db";
    private static final String DIM_GRAY     = "&#4b5563";
    private static final String WHITE        = "&f";
    private static final String GREEN        = "&#22c55e";
    private static final String RED          = "&#FF5555";
    private static final String PURPLE       = "&#a855f7";
    private static final String PIPE         = DIM_GRAY + "| ";

    // ── Scoreboard title ───────────────────────────────────────────────────────
    private static final String TITLE        = CYAN + "ᴍ ᴏ ʀ ᴏ ꜱ ᴍ ᴘ";

    // ── Per-player board registry ──────────────────────────────────────────────
    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();

    // ── Plugin references ──────────────────────────────────────────────────────
    private final MoroStats plugin;
    private final boolean    papiAvailable;

    // ── The single repeating update task ──────────────────────────────────────
    private BukkitTask updateTask;

    // ═════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Creates the manager. Call {@link #startUpdateTask()} afterwards.
     *
     * @param plugin        the owning plugin instance
     * @param papiAvailable {@code true} if PlaceholderAPI is installed
     */
    public ScoreboardManager(MoroStats plugin, boolean papiAvailable) {
        this.plugin        = plugin;
        this.papiAvailable = papiAvailable;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lifecycle — called from MoroStats.onEnable / onDisable
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Starts the repeating async task that refreshes every player's scoreboard
     * once per second (every 20 server ticks).
     *
     * <p>Call this <em>after</em> all online players have been given boards
     * (i.e. at the end of {@code onEnable}, after re-adding any players who
     * were online at reload time).
     */
    public void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Iterate over a snapshot of the entry set so ConcurrentModificationException
                // cannot occur even if a player quits during the iteration.
                for (Map.Entry<UUID, FastBoard> entry : boards.entrySet()) {
                    FastBoard board = entry.getValue();
                    if (board.isDeleted()) {
                        // Board was deleted (player quit) — clean up stale entry
                        boards.remove(entry.getKey());
                        continue;
                    }
                    // getPlayer() returns null if the player is offline —
                    // the board was not yet cleaned up, skip this tick.
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    updateBoard(board, player);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L);

        plugin.getLogger().info("Scoreboard update task started (async, every 20 ticks).");
    }

    /**
     * Cancels the update task and deletes every active board.
     * Call this from {@code onDisable}.
     */
    public void stopUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
        // Delete all active boards to send a REMOVE_OBJECTIVE packet to each client,
        // clearing the scoreboard from their screen gracefully.
        for (FastBoard board : boards.values()) {
            if (!board.isDeleted()) {
                board.delete();
            }
        }
        boards.clear();
        plugin.getLogger().info("All scoreboards removed and update task stopped.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Player join / quit — called from PlayerListener
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new {@link FastBoard} for the joining player and performs
     * an immediate first render so they see the scoreboard instantly.
     *
     * @param player the player who just joined
     */
    public void addPlayer(Player player) {
        // If a stale board exists (e.g. from a /reload), delete it first.
        removePlayer(player);

        FastBoard board = new FastBoard(player);
        boards.put(player.getUniqueId(), board);
        // Render immediately — don't wait for the next update tick
        updateBoard(board, player);

        plugin.getLogger().fine("Scoreboard created for " + player.getName());
    }

    /**
     * Deletes the {@link FastBoard} for the quitting player, sending the client
     * a packet to remove the scoreboard from their screen.
     *
     * @param player the player who just quit
     */
    public void removePlayer(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null && !board.isDeleted()) {
            board.delete();
            plugin.getLogger().fine("Scoreboard removed for " + player.getName());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Core rendering
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Builds the complete line list for the scoreboard and pushes it to
     * {@link FastBoard} in one call. FastBoard diffs the new lines against the
     * previous state and only sends packets for lines that actually changed,
     * minimising bandwidth and client-side flicker even further.
     *
     * @param board  the player's FastBoard instance
     * @param player the player whose data is being rendered
     */
    private void updateBoard(FastBoard board, Player player) {
        // Update title every tick so the cyan color pulses correctly if you
        // ever add a gradient animation. Static for now.
        board.updateTitle(color(TITLE));

        // ── Resolve all placeholders ───────────────────────────────────────
        // We resolve them all at once rather than inline so the code stays
        // readable and we can add fallback handling in one place.
        String playerName = player.getName();
        String ping       = String.valueOf(player.getPing());
        String kills      = resolvePapi(player, "%statistic_player_kills%");
        String deaths     = resolvePapi(player, "%statistic_deaths%");
        String time       = resolvePapi(player, "%statistic_time_played%");

        // ── Build lines (index 0 = top, displayed top-to-bottom) ──────────
        List<String> lines = Arrays.asList(
            // Line 1  — spacer
            "",
            // Line 2  — PROFILE section header
            color(HEADER_PROFILE),
            // Line 3  — player name
            color(PIPE + WHITE + playerName),
            // Line 4  — ping
            color(PIPE + CYAN + "Ping: " + WHITE + ping + " ms"),
            // Line 5  — spacer
            "",
            // Line 6  — STATS section header
            color(HEADER_STATS),
            // Line 7  — kills
            color(PIPE + GREEN + "Kills: " + WHITE + kills),
            // Line 8  — deaths
            color(PIPE + RED + "Deaths: " + WHITE + deaths),
            // Line 9  — playtime
            color(PIPE + PURPLE + "Time: " + WHITE + time),
            // Line 10 — spacer
            "",
            // Line 11 — server footer
            color(GREEN + "morosmp.net")
        );

        board.updateLines(lines);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Translates &#RRGGBB and &X codes via {@link ColorUtil}.
     */
    private String color(String text) {
        return ColorUtil.translate(text);
    }

    /**
     * Resolves a PlaceholderAPI placeholder for the given player.
     *
     * <p>Returns {@code "N/A"} if PAPI is not installed, so the scoreboard
     * never shows a raw {@code %placeholder%} token to the player.
     *
     * @param player      the player context
     * @param placeholder the raw placeholder string, e.g. {@code %statistic_deaths%}
     * @return the resolved value, or {@code "N/A"} if unavailable
     */
    private String resolvePapi(Player player, String placeholder) {
        if (!papiAvailable) return "N/A";
        try {
            String result = PlaceholderAPI.setPlaceholders(player, placeholder);
            // If PAPI returns the placeholder unchanged, it means no expansion handled it
            return (result == null || result.equals(placeholder)) ? "N/A" : result;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "Failed to resolve placeholder '" + placeholder
                    + "' for player " + player.getName() + ": " + e.getMessage());
            return "N/A";
        }
    }
}
