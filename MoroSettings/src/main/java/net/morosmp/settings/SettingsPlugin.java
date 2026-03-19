package net.morosmp.settings;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SettingsPlugin extends JavaPlugin {

    private SettingsManager settingsManager;

    /**
     * In-memory: tracks the last player each player sent/received a PM from,
     * so /r knows who to reply to. Cleared on server restart (intentional).
     */
    private final Map<UUID, UUID> lastMessaged = new HashMap<>();

    @Override
    public void onEnable() {
        saveResource("settings.yml", false);
        settingsManager = new SettingsManager(this);

        // /settings
        SettingsCommand settingsCmd = new SettingsCommand(this, settingsManager);
        getCommand("settings").setExecutor(settingsCmd);

        // /msg and /r
        MsgCommand msgCmd = new MsgCommand(this, settingsManager);
        getCommand("msg").setExecutor(msgCmd);
        getCommand("msg").setTabCompleter(msgCmd);
        getCommand("r").setExecutor(msgCmd);

        // GUI click listener (singleton)
        getServer().getPluginManager()
                .registerEvents(new SettingsGUI(this, settingsManager), this);

        getLogger().info("MoroSettings enabled.");
    }

    public SettingsManager getSettingsManager() { return settingsManager; }
    public Map<UUID, UUID>  getLastMessaged()   { return lastMessaged;    }
}
