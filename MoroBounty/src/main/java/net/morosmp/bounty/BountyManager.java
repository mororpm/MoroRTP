package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * BountyManager — single source of truth for all bounty data.
 *
 * Storage: SQLite (plugins/MoroBounty/database.db)
 *
 * Table schema:
 *   bounties (
 *       uuid         TEXT PRIMARY KEY,   -- victim player UUID
 *       sponsor_uuid TEXT NOT NULL,
 *       target_name  TEXT NOT NULL,      -- victim's name at placement time
 *       item_base64  TEXT NOT NULL,      -- Base64 Paper-serialized ItemStack
 *       item_desc    TEXT NOT NULL,      -- e.g. "10x DIAMOND" (human label)
 *       amount       INTEGER NOT NULL,   -- item count (consumed by Python API)
 *       timestamp    INTEGER NOT NULL    -- placement time, Unix millis
 *   )
 *
 * Architecture — cache + async writes:
 *
 *   ALL reads (hasBounty, getBountyItem, etc.) are served from an in-memory
 *   ConcurrentHashMap. They are always instant and main-thread safe.
 *
 *   ALL writes (setBounty, claimAndRemoveBounty, cancelBounty) update the
 *   cache synchronously on the calling thread, then fire the SQL statement
 *   on an async BukkitScheduler task so the main thread is never blocked.
 *
 *   On startup the cache is loaded from the database via an async task.
 *   This completes before any player can join, so reads are always warm.
 *
 *   SQLite WAL mode is enabled: the Python API (API.py) can SELECT from
 *   the same .db file concurrently without file-locking conflicts.
 */
public class BountyManager {

    // ── SQL ───────────────────────────────────────────────────────────────────
    private static final String SQL_CREATE = """
            CREATE TABLE IF NOT EXISTS bounties (
                uuid         TEXT    PRIMARY KEY,
                sponsor_uuid TEXT    NOT NULL,
                target_name  TEXT    NOT NULL,
                item_base64  TEXT    NOT NULL,
                item_desc    TEXT    NOT NULL,
                amount       INTEGER NOT NULL,
                timestamp    INTEGER NOT NULL
            )""";

    private static final String SQL_UPSERT =
            "INSERT OR REPLACE INTO bounties "
            + "(uuid, sponsor_uuid, target_name, item_base64, item_desc, amount, timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_DELETE = "DELETE FROM bounties WHERE uuid = ?";
    private static final String SQL_ALL    =
            "SELECT uuid, sponsor_uuid, target_name, item_base64, item_desc, amount, timestamp "
            + "FROM bounties";

    // ── Inner record (one database row) ──────────────────────────────────────
    private static final class BountyRecord {
        final UUID   sponsorUuid;
        final String targetName;
        final String itemBase64;
        final String itemDesc;
        final int    amount;
        final long   timestamp;

        BountyRecord(UUID s, String tn, String b64, String desc, int amt, long ts) {
            sponsorUuid = s; targetName = tn; itemBase64 = b64;
            itemDesc = desc; amount = amt; timestamp = ts;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final MoroBounty              plugin;
    private       Connection              connection;
    private final Map<UUID, BountyRecord> cache = new ConcurrentHashMap<>();

    // =========================================================================
    // Constructor
    // =========================================================================

    public BountyManager(MoroBounty plugin) {
        this.plugin = plugin;
        initDatabase();
        loadCacheAsync();
    }

    // =========================================================================
    // Database lifecycle
    // =========================================================================

    /**
     * Opens the SQLite connection, enables WAL mode, and creates the table.
     * Runs on the main thread — SQLite file creation is fast enough for startup.
     */
    private void initDatabase() {
        plugin.getDataFolder().mkdirs();
        File dbFile = new File(plugin.getDataFolder(), "database.db");
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement st = connection.createStatement()) {
                // WAL: Python API can read while Java is writing
                st.execute("PRAGMA journal_mode=WAL");
                // NORMAL is safe for WAL and faster than FULL
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute(SQL_CREATE);
            }
            plugin.getLogger().info(
                    "[BountyManager] Database ready: " + dbFile.getPath());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[BountyManager] Database initialisation failed!", e);
        }
    }

    /**
     * Loads all rows from the database into the in-memory cache asynchronously.
     * Because plugins enable before any player login packets are processed, the
     * cache is fully warm before any GUI or command can be triggered.
     */
    private void loadCacheAsync() {
        if (connection == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(SQL_ALL)) {

                int n = 0;
                while (rs.next()) {
                    cache.put(
                        UUID.fromString(rs.getString("uuid")),
                        new BountyRecord(
                            UUID.fromString(rs.getString("sponsor_uuid")),
                            rs.getString("target_name"),
                            rs.getString("item_base64"),
                            rs.getString("item_desc"),
                            rs.getInt("amount"),
                            rs.getLong("timestamp")
                        )
                    );
                    n++;
                }
                plugin.getLogger().info(
                        "[BountyManager] Cache loaded — " + n + " active bounty(ies).");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[BountyManager] Failed to load cache!", e);
            }
        });
    }

    /** Closes the database connection. Call from onDisable(). */
    public void closeConnection() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[BountyManager] Error closing connection.", e);
        }
    }

    // =========================================================================
    // ItemStack serialization (Paper 1.20.4+)
    // =========================================================================

    public String serializeItem(ItemStack item) {
        if (item == null) return null;
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[BountyManager] Deserialize failed: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Read API — all served from cache, always synchronous
    // =========================================================================

    public boolean hasBounty(UUID victimUuid) {
        return cache.containsKey(victimUuid);
    }

    public List<UUID> getAllBountyVictimUuids() {
        return new ArrayList<>(cache.keySet());
    }

    /**
     * Reads and deserializes the reward item WITHOUT removing it from storage.
     * Used by the Browse GUI to display reward lore on each head.
     */
    public ItemStack getBountyItem(UUID victimUuid) {
        BountyRecord r = cache.get(victimUuid);
        return r != null ? deserializeItem(r.itemBase64) : null;
    }

    /** Human-readable label, e.g. "10x DIAMOND". Null if no bounty exists. */
    public String getBountyDescription(UUID victimUuid) {
        BountyRecord r = cache.get(victimUuid);
        return r != null ? r.itemDesc : null;
    }

    /** UUID of the sponsor who placed this bounty. Null if none. */
    public UUID getSponsorUuid(UUID victimUuid) {
        BountyRecord r = cache.get(victimUuid);
        return r != null ? r.sponsorUuid : null;
    }

    // =========================================================================
    // Write API — cache updated synchronously, SQL executed asynchronously
    // =========================================================================

    /**
     * Stores a bounty.
     * Cache update is synchronous so all subsequent reads are immediately
     * consistent. The SQL INSERT OR REPLACE runs on an async thread.
     *
     * <p>Caller is responsible for checking {@link #hasBounty} first.
     */
    public void setBounty(UUID sponsorUuid, UUID victimUuid, ItemStack item) {
        String base64 = serializeItem(item);
        if (base64 == null) {
            plugin.getLogger().warning(
                    "[BountyManager] setBounty() — item not serializable, aborted.");
            return;
        }

        String tName = Bukkit.getOfflinePlayer(victimUuid).getName();
        if (tName == null) tName = victimUuid.toString().substring(0, 8) + "…";

        String itemDesc  = item.getAmount() + "x " + item.getType().name();
        int    amount    = item.getAmount();
        long   timestamp = System.currentTimeMillis();

        // Synchronous cache write — instantly visible to all callers
        cache.put(victimUuid, new BountyRecord(
                sponsorUuid, tName, base64, itemDesc, amount, timestamp));

        // Async SQL write
        final String fName = tName;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(SQL_UPSERT)) {
                ps.setString(1, victimUuid.toString());
                ps.setString(2, sponsorUuid.toString());
                ps.setString(3, fName);
                ps.setString(4, base64);
                ps.setString(5, itemDesc);
                ps.setInt   (6, amount);
                ps.setLong  (7, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[BountyManager] Failed to write bounty for victim "
                        + victimUuid, e);
            }
        });
    }

    /**
     * Atomically claims and removes the bounty.
     * Removes from cache immediately; SQL DELETE runs async.
     * Returns the deserialized reward item, or null if none/corrupted.
     */
    public ItemStack claimAndRemoveBounty(UUID victimUuid) {
        BountyRecord record = cache.remove(victimUuid);
        if (record == null) return null;
        asyncDelete(victimUuid);
        return deserializeItem(record.itemBase64);
    }

    /**
     * Cancels a bounty without dropping the item.
     * Sponsors cannot recall their bounty — the item is intentionally lost.
     */
    public void cancelBounty(UUID victimUuid) {
        cache.remove(victimUuid);
        asyncDelete(victimUuid);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void asyncDelete(UUID victimUuid) {
        if (connection == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(SQL_DELETE)) {
                ps.setString(1, victimUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[BountyManager] Failed to delete bounty for " + victimUuid, e);
            }
        });
    }
}
