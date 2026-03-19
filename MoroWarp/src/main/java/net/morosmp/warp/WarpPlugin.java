package net.morosmp.warp;

import org.bukkit.plugin.java.JavaPlugin;

public class WarpPlugin extends JavaPlugin {

    private WarpManager warpManager;

    @Override
    public void onEnable() {
        saveResource("warps.yml", false);
        warpManager = new WarpManager(this);

        WarpCommand cmd = new WarpCommand(this, warpManager);
        getCommand("setwarp").setExecutor(cmd);
        getCommand("warp").setExecutor(cmd);
        getCommand("delwarp").setExecutor(cmd);
        getCommand("warp").setTabCompleter(cmd);
        getCommand("delwarp").setTabCompleter(cmd);

        getLogger().info("MoroWarp enabled. Warps loaded: "
                + warpManager.getWarpNames().size());
    }

    public WarpManager getWarpManager() { return warpManager; }
}
