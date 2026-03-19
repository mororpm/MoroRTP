package net.morosmp.bounty;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;

/**
 * BountyManager — the single source of truth for all bounty data.
 *
 * Storage format (bounties.yml):
 *   bounties:
 *     <victim-uuid>:
 *       sponsor:   <placer-uuid>
 *       item:      <Base64-encoded ItemStack>
 *       itemDesc:  "10x DIAMOND"          # human-readable, debug only
 *       timestamp: 1710000000000
 *
 * Serialization strategy:
 *   Paper 1.21 provides ItemStack#serializeAsBytes() / ItemStack.deserializeBytes()
 *   which handles ALL item data (NBT, custom model data, enchantments, lore, etc.)
 *   and is guaranteed to work across reloads on the same server version.
 *   We Base64-encode the raw bytes so they fit safely in a YAML string value.
 */
public class BountyManager {

    private static final String BOUNTIES_PATH = "bounties.";

    private final MoroBounty plugin;
    private File              dataFile;
    private FileConfiguration dataConfig;

    public BountyManager(MoroBounty plugin) {
        this.plugin = plugin;
        loadDataFile();
    }

    // -------------------------------------------------------------------------
    // YAML I/O
    // -------------------------------------------------------------------------

    private void loadDataFile() {
        dataFile   = new File(plugin.getDataFolder(), "bounties.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /** Flushes the in-memory config to disk. Called after every write operation. */
    private void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save bounties.yml!", e);
        }
    }

    // -------------------------------------------------------------------------
    // Serialization helpers  (Paper 1.21 API — no external libraries needed)
    // -------------------------------------------------------------------------

    /**
     * Converts an ItemStack into a Base64 string using Paper's native byte serializer.
     * This preserves ALL item data including custom names, lore, enchantments, NBT.
     */
    public String serializeItem(ItemStack item) {
        if (item == null) return null;
        byte[] bytes = item.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Reconstructs an ItemStack from a Base64 string produced by serializeItem().
     * Returns null if the string is null, empty, or the data is corrupt.
     */
    public ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize bounty item: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Bounty CRUD
    // -------------------------------------------------------------------------

    /** Returns true if victim currently has an active bounty. */
    public boolean hasBounty(UUID victimUuid) {
        return dataConfig.contains(BOUNTIES_PATH + victimUuid);
    }

    /**
     * Places a new bounty. Overwrites any previous bounty on the same victim
     * (the old item is lost — callers should check hasBounty() first if needed).
     *
     * @param sponsorUuid  UUID of the player placing the bounty
     * @param victimUuid   UUID of the target
     * @param item         The physical ItemStack deposited as the reward
     */
    public void setBounty(UUID sponsorUuid, UUID victimUuid, ItemStack item) {
        String serialized = serializeItem(item);
        if (serialized == null) {
            plugin.getLogger().warning("setBounty() called with a null/unserializable item — bounty not placed.");
            return;
        }

        String path = BOUNTIES_PATH + victimUuid;
        dataConfig.set(path + ".sponsor",   sponsorUuid.toString());
        dataConfig.set(path + ".item",      serialized);
        dataConfig.set(path + ".itemDesc",  item.getAmount() + "x " + item.getType().name());
        dataConfig.set(path + ".timestamp", System.currentTimeMillis());
        save();
    }

    /**
     * Retrieves and removes the bounty item from storage for the given victim.
     * Returns null if no bounty exists or the item cannot be deserialized.
     */
    public ItemStack claimAndRemoveBounty(UUID victimUuid) {
        if (!hasBounty(victimUuid)) return null;

        String base64 = dataConfig.getString(BOUNTIES_PATH + victimUuid + ".item");
        ItemStack item = deserializeItem(base64);

        // Remove the entire victim node whether or not deserialization succeeded,
        // so a corrupt entry doesn't block future bounties on the same player.
        dataConfig.set(BOUNTIES_PATH + victimUuid.toString(), null);
        save();

        return item;
    }

    /**
     * Cancels (deletes) a bounty without dropping the item. The deposited item
     * is permanently lost — intentional; sponsors cannot recall a bounty mid-hunt.
     */
    public void cancelBounty(UUID victimUuid) {
        dataConfig.set(BOUNTIES_PATH + victimUuid.toString(), null);
        save();
    }

    /**
     * Returns a human-readable description of the bounty item for chat messages,
     * e.g. "10x DIAMOND". Returns null if no bounty exists.
     */
    public String getBountyDescription(UUID victimUuid) {
        return dataConfig.getString(BOUNTIES_PATH + victimUuid + ".itemDesc");
    }

    /** Returns the UUID of whoever placed this bounty, or null if none exists. */
    public UUID getSponsorUuid(UUID victimUuid) {
        String raw = dataConfig.getString(BOUNTIES_PATH + victimUuid + ".sponsor");
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }
}
