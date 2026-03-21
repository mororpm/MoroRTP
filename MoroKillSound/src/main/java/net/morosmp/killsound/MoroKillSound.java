package net.morosmp.killsound;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MoroKillSound — plays configurable sounds to a player when they get a kill.
 *
 * FIX: The previous version hard-coded three specific Sound enum values
 * (ENTITY_ARROW_HIT_PLAYER, ENTITY_EXPERIENCE_ORB_PICKUP, ENTITY_WITHER_SPAWN),
 * completely ignoring the config.yml. This meant:
 *   1. The config values had zero effect on the actual sounds played.
 *   2. If a sound enum was renamed or removed in a Minecraft update, the
 *      plugin would crash at class-load time with a NoSuchFieldError.
 *
 * FIXED:
 *   - Sound name, volume, and pitch are now read from config.yml at runtime.
 *   - Sound.valueOf() is wrapped in a try-catch; if the configured name is
 *     invalid, it logs a warning and falls back to UI_BUTTON_CLICK so the
 *     plugin never crashes over a config typo.
 *   - play-to-all: if true, the sound is broadcast to all players within
 *     the configured radius around the kill location.
 *   - saveDefaultConfig() is now called in onEnable() so the config file
 *     is written to disk on first run (was missing in the original).
 */
public class MoroKillSound extends JavaPlugin implements Listener {

    /** Fallback used when the configured sound name is invalid. */
    private static final Sound FALLBACK_SOUND = Sound.UI_BUTTON_CLICK;

    @Override
    public void onEnable() {
        // Write default config.yml to the data folder if it doesn't exist.
        // Without this call, getConfig() returns empty defaults and the
        // config file is never created on disk.
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MoroKillSound enabled — sounds driven by config.yml.");
    }

    /**
     * Fired when a player dies.
     * Priority = HIGH so we run after combat plugins that may set or clear
     * the killer reference, but before MONITOR-level stat trackers.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return; // no player killer — nothing to do

        // ── Read config values ────────────────────────────────────────────────
        String configName = getConfig().getString("sound.name",    "ENTITY_WITHER_SPAWN");
        float  volume     = (float) getConfig().getDouble("sound.volume", 1.0);
        float  pitch      = (float) getConfig().getDouble("sound.pitch",  1.0);
        boolean playToAll = getConfig().getBoolean("sound.play-to-all", false);
        double  radius    = getConfig().getDouble("sound.radius", 20.0);

        // ── Resolve Sound enum with fallback ──────────────────────────────────
        // Sound.valueOf() throws IllegalArgumentException for unknown names.
        // We catch it so a typo in config.yml never brings the plugin down.
        Sound sound;
        try {
            sound = Sound.valueOf(configName.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning(
                    "[KillSound] Unknown sound '" + configName
                    + "' in config.yml — falling back to "
                    + FALLBACK_SOUND.name()
                    + ". Fix 'sound.name' and use /reload to apply.");
            sound = FALLBACK_SOUND;
        }

        // ── Play sound ────────────────────────────────────────────────────────
        if (playToAll) {
            // Broadcast to all players within `radius` blocks of the kill location
            final Sound finalSound = sound;
            final float  fVol = volume, fPitch = pitch;

            killer.getWorld()
                    .getPlayers()
                    .stream()
                    .filter(p -> p.getLocation()
                            .distanceSquared(killer.getLocation()) <= radius * radius)
                    .forEach(p -> p.playSound(killer.getLocation(), finalSound, fVol, fPitch));
        } else {
            // Play only to the killer
            killer.playSound(killer.getLocation(), sound, volume, pitch);
        }
    }
}
