package net.morosmp.stats;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MoroStats — main plugin class.
 *
 * <h2>Startup sequence</h2>
 * <ol>
 *   <li>Detect whether PlaceholderAPI is installed.</li>
 *   <li>Create {@link ScoreboardManager}.</li>
 *   <li>Register {@link PlayerListener} to handle join/quit.</li>
 *   <li>Give scoreboards to any players already online
 *       (handles {@code /reload} or hot-loading the plugin mid-session).</li>
 *   <li>Start the async 1-second update task.</li>
 * </ol>
 *
 * <h2>Shutdown sequence</h2>
 * <ol>
 *   <li>Cancel the update task.</li>
 *   <li>Delete every active {@link fr.mrmicky.fastboard.FastBoard}, which sends
 *       a {@code REMOVE_OBJECTIVE} packet to each client — the scoreboard
 *       disappears from their screen gracefully.</li>
 * </ol>
 */
public class MoroStats extends JavaPlugin {

    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        // ── 1. Detect PlaceholderAPI ───────────────────────────────────────────
        boolean papiAvailable = getServer()
                .getPluginManager()
                .getPlugin("PlaceholderAPI") != null;

        if (papiAvailable) {
            getLogger().info("PlaceholderAPI detected — stat placeholders enabled.");
        } else {
            getLogger().warning(
                    "PlaceholderAPI not found. Stat placeholders will show 'N/A'. "
                    + "Install PlaceholderAPI and the Statistic expansion for full functionality.");
        }

        // ── 2. Create the scoreboard manager ──────────────────────────────────
        scoreboardManager = new ScoreboardManager(this, papiAvailable);

        // ── 3. Register the join / quit listener ──────────────────────────────
        getServer().getPluginManager()
                .registerEvents(new PlayerListener(scoreboardManager), this);

        // ── 4. Give scoreboards to players already online (reload support) ────
        // Without this loop, anyone online at plugin-load time would have no
        // scoreboard until they reconnect.
        int reloaded = 0;
        for (Player player : getServer().getOnlinePlayers()) {
            scoreboardManager.addPlayer(player);
            reloaded++;
        }
        if (reloaded > 0) {
            getLogger().info("Gave scoreboards to " + reloaded
                    + " already-online player(s) (reload detected).");
        }

        // ── 5. Start the async update task (every 20 ticks = 1 second) ────────
        // This MUST be called AFTER step 4 so the first tick doesn't try to
        // update boards for players who haven't been added yet.
        scoreboardManager.startUpdateTask();

        getLogger().info("MoroStats enabled — scoreboard running.");
    }

    @Override
    public void onDisable() {
        // Cancel the update task and clean up all FastBoard instances.
        // FastBoard.delete() sends REMOVE_OBJECTIVE to each client, so the
        // sidebar disappears immediately rather than ghosting until the next
        // login.
        if (scoreboardManager != null) {
            scoreboardManager.stopUpdateTask();
        }
        getLogger().info("MoroStats disabled — all scoreboards removed.");
    }

    // ── Public accessor (useful if other plugin classes need the manager) ──────
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}
