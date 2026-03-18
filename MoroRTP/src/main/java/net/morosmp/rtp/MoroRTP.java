package net.morosmp.rtp;

import org.bukkit.plugin.java.JavaPlugin;

public class MoroRTP extends JavaPlugin {
    private TeleportManager teleportManager;

    @Override
    public void onEnable() {
        teleportManager = new TeleportManager(this);
        
        getCommand("rtp").setExecutor(new RTPCommand(this));
        
        getServer().getPluginManager().registerEvents(new RTPGUI(this), this);
        getServer().getPluginManager().registerEvents(new RTPListener(teleportManager), this);
        
        getLogger().info("MoroRTP loaded! Clean compile without trash files.");
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }
}