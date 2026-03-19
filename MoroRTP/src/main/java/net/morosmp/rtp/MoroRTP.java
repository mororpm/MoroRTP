package net.morosmp.rtp;

import org.bukkit.plugin.java.JavaPlugin;

public class MoroRTP extends JavaPlugin {
    private TeleportManager teleportManager;
    // Singleton GUI instance — registered ONCE so InventoryClickEvent is never missed
    private RTPGUI rtpGui;

    @Override
    public void onEnable() {
        // FIX 1: saveDefaultConfig() MUST come first so getConfig() reads config.yml
        // Without this call, getConfig().getString() returns the fallback "Unknown" value
        // for every key because the config file never gets written to disk.
        saveDefaultConfig();

        teleportManager = new TeleportManager(this);

        // FIX 2: Create the RTPGUI singleton here and store it so RTPCommand can reuse
        // the same instance. Creating new RTPGUI(plugin) inside the command was the root
        // cause of the unclickable bug — the freshly-created instance was never registered
        // as a Listener, so its InventoryClickEvent handler never fired.
        rtpGui = new RTPGUI(this);

        getCommand("rtp").setExecutor(new RTPCommand(this));

        // Register the SINGLE gui instance as a Listener
        getServer().getPluginManager().registerEvents(rtpGui, this);
        getServer().getPluginManager().registerEvents(new RTPListener(teleportManager), this);

        getLogger().info("MoroRTP loaded! saveDefaultConfig applied, GUI singleton registered.");
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    /** Returns the singleton RTPGUI instance. Used by RTPCommand to open the menu. */
    public RTPGUI getRtpGui() {
        return rtpGui;
    }
}
