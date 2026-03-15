package net.morosmp.rtp;

import org.bukkit.plugin.java.JavaPlugin;

public class MoroRTP extends JavaPlugin {
    private TeleportManager teleportManager;
    private ConfigManager configManager;
    private VaultHook vaultHook;
    private WorldUnlockManager worldUnlockManager; // ДОБАВЛЕНО

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Инициализация менеджеров
        worldUnlockManager = new WorldUnlockManager(this); // ДОБАВЛЕНО
        teleportManager = new TeleportManager(this);
        configManager = new ConfigManager(this);
        vaultHook = new VaultHook(this);

        getCommand("rtp").setExecutor(new RTPCommand(this));
        getServer().getPluginManager().registerEvents(new RTPListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(worldUnlockManager, this); // ДОБАВЛЕНО

        getLogger().info("[Moro] RTP module successfully loaded! Version 1.21.11");
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public WorldUnlockManager getWorldUnlockManager() {
        return worldUnlockManager;
    }
}