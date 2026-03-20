package net.morosmp.bounty;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * BountyManager — single source of truth for all bounty data.
 *
 * Storage format (bounties.yml):
 *   bounties:
 *     <victim-uuid>:
 *       sponsor:   <placer-uuid>
 *       item:      <Base64 ItemStack>
 *       itemDesc:  "10x DIAMOND"     # human-readable label, logs/UI only
 *       timestamp: 1710000000000
 *
 * Serialization:
 *   ItemStack#serializeAsBytes() / ItemStack.deserializeBytes() — Paper 1.20.4+.
 *   Preserves all data: name, lore, enchantments, NBT.
 */
public class BountyManager {

    private static final String BOUNTIES_PATH = "bounties.";

    private final MoroBounty      plugin;
    private       File            dataFile;
    private       FileConfiguration dataConfig;

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

    private void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save bounties.yml!", e);
        }
    }

    // -------------------------------------------------------------------------
    // Serialization (Paper 1.21 API)
    // -------------------------------------------------------------------------

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
                    "Failed to deserialize bounty item: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Bounty CRUD
    // -------------------------------------------------------------------------

    /** Returns true if a victim currently has an active bounty. */
    public boolean hasBounty(UUID victimUuid) {
        return dataConfig.contains(BOUNTIES_PATH + victimUuid);
    }

    /**
     * Sets a bounty. Caller must check hasBounty() before calling.
     * Overwrite behavior: previous item is permanently lost.
     */
    public void setBounty(UUID sponsorUuid, UUID victimUuid, ItemStack item) {
        String serialized = serializeItem(item);
        if (serialized == null) {
            plugin.getLogger().warning(
                    "setBounty() called with null/non-serializable item — bounty not set.");
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
     * Atomic operation: reads ItemStack from YAML and immediately removes the record.
     * Used on PlayerDeathEvent. Returns null if no bounty or data is corrupted.
     */
    public ItemStack claimAndRemoveBounty(UUID victimUuid) {
        if (!hasBounty(victimUuid)) return null;
        String base64  = dataConfig.getString(BOUNTIES_PATH + victimUuid + ".item");
        ItemStack item = deserializeItem(base64);
        // Always delete the node — corrupted records must not block future bounties.
        dataConfig.set(BOUNTIES_PATH + victimUuid.toString(), null);
        save();
        return item;
    }

    /**
     * [NEW] Reads and deserializes bounty item WITHOUT deleting from storage.
     * Used by GUI to render reward details in head lore.
     * Returns null if there is no bounty or data is corrupted.
     */
    public ItemStack getBountyItem(UUID victimUuid) {
        if (!hasBounty(victimUuid)) return null;
        String base64 = dataConfig.getString(BOUNTIES_PATH + victimUuid + ".item");
        return deserializeItem(base64);
    }

    /**
     * [NEW] Returns ordered list of UUIDs for all victims with active bounties.
     * Order = YAML insertion order (chronological).
     * Used by browse GUI for paginated head list.
     */
    public List<UUID> getAllBountyVictimUuids() {
        ConfigurationSection section = dataConfig.getConfigurationSection("bounties");
        if (section == null) return new ArrayList<>();
        List<UUID> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                result.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // Corrupted UUID key — skip.
            }
        }
        return result;
    }

    /** Cancels bounty without drop. Item is intentionally lost (no bounty recall). */
    public void cancelBounty(UUID victimUuid) {
        dataConfig.set(BOUNTIES_PATH + victimUuid.toString(), null);
        save();
    }

    /** "10x DIAMOND" etc. Null if no bounty exists. */
    public String getBountyDescription(UUID victimUuid) {
        return dataConfig.getString(BOUNTIES_PATH + victimUuid + ".itemDesc");
    }

    /** Sponsor UUID or null. */
    public UUID getSponsorUuid(UUID victimUuid) {
        String raw = dataConfig.getString(BOUNTIES_PATH + victimUuid + ".sponsor");
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }
}
