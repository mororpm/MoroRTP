package net.morosmp.settings;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SettingsManager — persists per-player boolean toggles in settings.yml.
 *
 * Current settings:
 *   pm-enabled   (default true)  — controls /msg and /r visibility
 *   kill-sounds  (default true)  — readable by MoroKillSound via static helper
 *
 * Integration with MoroKillSound:
 *   MoroKillSound can call SettingsManager#isKillSoundsEnabled(uuid) directly
 *   IF MoroSettings is a soft-depend. Alternatively, MoroKillSound can read
 *   the plugin's settings.yml from disk using Bukkit.getPluginManager().getPlugin().
 */
public class SettingsManager {

    private final SettingsPlugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public SettingsManager(SettingsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        dataFile   = new File(plugin.getDataFolder(), "settings.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try { dataConfig.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save settings.yml", e);
        }
    }

    // ── Getters ────────────────────────────────────────────────────────

    /** Returns true if the player has private messages enabled (default: true). */
    public boolean isPmEnabled(UUID uuid) {
        return dataConfig.getBoolean("settings." + uuid + ".pm-enabled", true);
    }

    /** Returns true if the player has kill sounds enabled (default: true). */
    public boolean isKillSoundsEnabled(UUID uuid) {
        return dataConfig.getBoolean("settings." + uuid + ".kill-sounds", true);
    }

    // ── Toggles ───────────────────────────────────────────────────────

    public void togglePm(UUID uuid) {
        dataConfig.set("settings." + uuid + ".pm-enabled", !isPmEnabled(uuid));
        save();
    }

    public void toggleKillSounds(UUID uuid) {
        dataConfig.set("settings." + uuid + ".kill-sounds", !isKillSoundsEnabled(uuid));
        save();
    }
}
