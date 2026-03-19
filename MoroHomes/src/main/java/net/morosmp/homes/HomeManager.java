package net.morosmp.homes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * HomeManager — YAML-backed storage for the multi-home system.
 *
 * YAML layout (homes.yml):
 * ─────────────────────────────────────────────────────────────────────────────
 *   homes:
 *     <player-uuid>:
 *       max-homes: 1            ← number of slots the player has unlocked
 *       list:
 *         home:                 ← home name (lowercase, alphanumeric)
 *           world: world
 *           x: 100.5
 *           y: 64.0
 *           z: 200.5
 *           yaw: 0.0
 *           pitch: 0.0
 *         base:
 *           ...
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Design decisions:
 *   - max-homes is stored PER PLAYER so admin commands or future events can
 *     grant/revoke slots individually without touching config.yml.
 *   - A new player who has never used /sethome has NO entry in the file at all;
 *     getMaxHomes() falls back to config's default-max-homes transparently.
 *   - Home names are lowercased on write/read so "Home" and "home" are identical.
 */
public class HomeManager {

    // YAML key prefixes
    private static final String ROOT          = "homes.";
    private static final String KEY_MAX       = ".max-homes";
    private static final String KEY_LIST      = ".list.";

    private final HomesPlugin        plugin;
    private       File               dataFile;
    private       FileConfiguration  dataConfig;

    public HomeManager(HomesPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // =========================================================================
    // I/O
    // =========================================================================

    private void load() {
        dataFile   = new File(plugin.getDataFolder(), "homes.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save homes.yml!", e);
        }
    }

    // =========================================================================
    // Max-homes (slot limit)
    // =========================================================================

    /**
     * Returns the number of home slots this player has unlocked.
     * Falls back to config's default-max-homes if no entry exists yet.
     */
    public int getMaxHomes(UUID uuid) {
        String key = ROOT + uuid + KEY_MAX;
        if (dataConfig.contains(key)) {
            return dataConfig.getInt(key);
        }
        return plugin.getConfig().getInt("default-max-homes", 1);
    }

    /**
     * Persists a new max-homes value for the player.
     * Called by BuyHomeCommand after successful payment.
     */
    public void setMaxHomes(UUID uuid, int max) {
        dataConfig.set(ROOT + uuid + KEY_MAX, max);
        save();
    }

    /**
     * Returns the Diamond cost to unlock the NEXT slot for this player.
     * Looks up upgrades.<nextSlot> in config.yml.
     * Returns -1 if no upgrade is configured for that slot (cap reached).
     */
    public int getUpgradeCost(UUID uuid) {
        int nextSlot = getMaxHomes(uuid) + 1;
        // upgrades section uses integer keys, e.g.  upgrades: { 2: 64, 3: 128 }
        ConfigurationSection upgrades = plugin.getConfig().getConfigurationSection("upgrades");
        if (upgrades == null) return -1;
        if (!upgrades.contains(String.valueOf(nextSlot))) return -1;
        return upgrades.getInt(String.valueOf(nextSlot));
    }

    // =========================================================================
    // Home CRUD
    // =========================================================================

    /**
     * Returns how many homes this player currently has saved.
     */
    public int getHomeCount(UUID uuid) {
        ConfigurationSection listSec = dataConfig.getConfigurationSection(ROOT + uuid + ".list");
        return listSec == null ? 0 : listSec.getKeys(false).size();
    }

    /**
     * Returns true if the player has a home with this exact name.
     * Names are normalised to lowercase.
     */
    public boolean hasHome(UUID uuid, String name) {
        return dataConfig.contains(ROOT + uuid + KEY_LIST + name.toLowerCase());
    }

    /**
     * Returns a sorted list of all home names this player has set.
     * Empty list if none.
     */
    public List<String> getHomeNames(UUID uuid) {
        ConfigurationSection listSec = dataConfig.getConfigurationSection(ROOT + uuid + ".list");
        if (listSec == null) return Collections.emptyList();
        List<String> names = new ArrayList<>(listSec.getKeys(false));
        Collections.sort(names);
        return names;
    }

    /**
     * Saves (or overwrites) a named home for the player.
     * The caller is responsible for checking the slot limit before calling this.
     */
    public void setHome(UUID uuid, String name, Location loc) {
        String p = ROOT + uuid + KEY_LIST + name.toLowerCase() + ".";
        dataConfig.set(p + "world", loc.getWorld().getName());
        dataConfig.set(p + "x",     loc.getX());
        dataConfig.set(p + "y",     loc.getY());
        dataConfig.set(p + "z",     loc.getZ());
        dataConfig.set(p + "yaw",   (double) loc.getYaw());
        dataConfig.set(p + "pitch", (double) loc.getPitch());
        save();
    }

    /**
     * Returns the Location of a named home, or null if:
     *   - the home does not exist, OR
     *   - the saved world no longer exists.
     */
    public Location getHome(UUID uuid, String name) {
        name = name.toLowerCase();
        if (!hasHome(uuid, name)) return null;

        String p     = ROOT + uuid + KEY_LIST + name + ".";
        String wName = dataConfig.getString(p + "world");
        World  world = Bukkit.getWorld(wName == null ? "" : wName);
        if (world == null) return null;

        return new Location(
                world,
                dataConfig.getDouble(p + "x"),
                dataConfig.getDouble(p + "y"),
                dataConfig.getDouble(p + "z"),
                (float) dataConfig.getDouble(p + "yaw"),
                (float) dataConfig.getDouble(p + "pitch")
        );
    }

    /**
     * Deletes a specific named home.
     * Does nothing if the home doesn't exist.
     */
    public void deleteHome(UUID uuid, String name) {
        dataConfig.set(ROOT + uuid + KEY_LIST + name.toLowerCase(), null);

        // If the player now has no homes AND no max-homes override, clean up
        // the entire player node to keep homes.yml tidy.
        ConfigurationSection listSec = dataConfig.getConfigurationSection(ROOT + uuid + ".list");
        boolean listEmpty = (listSec == null || listSec.getKeys(false).isEmpty());
        boolean noMaxOverride = !dataConfig.contains(ROOT + uuid + KEY_MAX);
        if (listEmpty && noMaxOverride) {
            dataConfig.set(ROOT + uuid.toString(), null);
        }

        save();
    }
}
