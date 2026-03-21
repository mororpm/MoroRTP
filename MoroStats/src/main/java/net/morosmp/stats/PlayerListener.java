package net.morosmp.stats;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PlayerListener — hooks into join and quit events to give each player
 * their personal scoreboard.
 *
 * <p>Priority is set to {@link EventPriority#MONITOR} on join so that
 * other plugins (e.g. permission managers) have already finished their
 * setup before we render the first frame of the scoreboard. This ensures
 * placeholders like {@code %luckperms_prefix%} resolve correctly on the
 * very first tick.
 */
public class PlayerListener implements Listener {

    private final ScoreboardManager scoreboardManager;

    public PlayerListener(ScoreboardManager scoreboardManager) {
        this.scoreboardManager = scoreboardManager;
    }

    /**
     * Gives the joining player a scoreboard.
     * Runs on MONITOR priority — all other plugins have already processed the join.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // addPlayer() creates the FastBoard and renders the first frame immediately,
        // so the player sees the scoreboard the instant they finish loading in.
        scoreboardManager.addPlayer(event.getPlayer());
    }

    /**
     * Removes the quitting player's scoreboard.
     * This sends a REMOVE_OBJECTIVE packet so the sidebar disappears cleanly
     * and does not ghost on the player's screen if they later reconnect.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        scoreboardManager.removePlayer(event.getPlayer());
    }
}
