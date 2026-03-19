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
 * BountyManager — единственный источник правды для всех данных баунти.
 *
 * Формат хранения (bounties.yml):
 *   bounties:
 *     <victim-uuid>:
 *       sponsor:   <placer-uuid>
 *       item:      <Base64 ItemStack>
 *       itemDesc:  "10x DIAMOND"     # человекочитаемый лейбл, только для логов
 *       timestamp: 1710000000000
 *
 * Сериализация:
 *   ItemStack#serializeAsBytes() / ItemStack.deserializeBytes() — Paper 1.20.4+.
 *   Сохраняет ВСЕ данные: имя, лор, зачарования, NBT.
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
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить bounties.yml!", e);
        }
    }

    // -------------------------------------------------------------------------
    // Сериализация (Paper 1.21 API)
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
                    "Не удалось десериализовать предмет баунти: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Bounty CRUD
    // -------------------------------------------------------------------------

    /** Возвращает true, если на жертву активен баунти. */
    public boolean hasBounty(UUID victimUuid) {
        return dataConfig.contains(BOUNTIES_PATH + victimUuid);
    }

    /**
     * Устанавливает баунти. Вызывающий обязан проверить hasBounty() перед вызовом.
     * Перезапись — старый предмет безвозвратно теряется.
     */
    public void setBounty(UUID sponsorUuid, UUID victimUuid, ItemStack item) {
        String serialized = serializeItem(item);
        if (serialized == null) {
            plugin.getLogger().warning(
                    "setBounty() вызван с null/несериализуемым предметом — баунти не установлен.");
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
     * Атомарная операция: читает ItemStack из YAML и немедленно удаляет запись.
     * Используется при PlayerDeathEvent. Возвращает null если нет баунти или данные битые.
     */
    public ItemStack claimAndRemoveBounty(UUID victimUuid) {
        if (!hasBounty(victimUuid)) return null;
        String base64  = dataConfig.getString(BOUNTIES_PATH + victimUuid + ".item");
        ItemStack item = deserializeItem(base64);
        // Удаляем узел в любом случае — битая запись не должна блокировать будущие баунти
        dataConfig.set(BOUNTIES_PATH + victimUuid.toString(), null);
        save();
        return item;
    }

    /**
     * [NEW] Читает и десериализует предмет баунти БЕЗ удаления из хранилища.
     * Используется GUI для отображения информации о награде в лоре головы.
     * Возвращает null, если баунти нет или данные повреждены.
     */
    public ItemStack getBountyItem(UUID victimUuid) {
        if (!hasBounty(victimUuid)) return null;
        String base64 = dataConfig.getString(BOUNTIES_PATH + victimUuid + ".item");
        return deserializeItem(base64);
    }

    /**
     * [NEW] Возвращает упорядоченный список UUID всех жертв с активными баунти.
     * Порядок = порядок вставки в YAML (хронологический).
     * Используется browse-GUI для отображения страниц голов.
     */
    public List<UUID> getAllBountyVictimUuids() {
        ConfigurationSection section = dataConfig.getConfigurationSection("bounties");
        if (section == null) return new ArrayList<>();
        List<UUID> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                result.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // Повреждённый UUID-ключ — пропускаем
            }
        }
        return result;
    }

    /** Отменяет баунти без дропа. Предмет теряется — намеренно (нельзя отозвать баунти). */
    public void cancelBounty(UUID victimUuid) {
        dataConfig.set(BOUNTIES_PATH + victimUuid.toString(), null);
        save();
    }

    /** "10x DIAMOND" и т.д. Null если баунти нет. */
    public String getBountyDescription(UUID victimUuid) {
        return dataConfig.getString(BOUNTIES_PATH + victimUuid + ".itemDesc");
    }

    /** UUID спонсора или null. */
    public UUID getSponsorUuid(UUID victimUuid) {
        String raw = dataConfig.getString(BOUNTIES_PATH + victimUuid + ".sponsor");
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }
}
