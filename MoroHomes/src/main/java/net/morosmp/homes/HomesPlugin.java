package net.morosmp.homes;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MoroHomes v2 — multi-home system with physical Diamond upgrades.
 *
 * Commands registered:
 *   /sethome <n>   — set a named home (subject to max-homes limit)
 *   /home [name]      — teleport to a named home
 *   /delhome <n>   — delete a named home
 *   /buyhome          — purchase the next home slot with physical Diamonds
 *   /homes            — list all current homes
 */
public class HomesPlugin extends JavaPlugin {

    private HomeManager homeManager;

    @Override
    public void onEnable() {
        // Write default config.yml and homes.yml to the data folder if absent
        saveDefaultConfig();
        saveResource("homes.yml", false);

        homeManager = new HomeManager(this);

        // /sethome, /home, /delhome, /homes — all handled by HomeCommand
        HomeCommand homeCmd = new HomeCommand(this, homeManager);
        getCommand("sethome").setExecutor(homeCmd);
        getCommand("sethome").setTabCompleter(homeCmd);
        getCommand("home").setExecutor(homeCmd);
        getCommand("home").setTabCompleter(homeCmd);
        getCommand("delhome").setExecutor(homeCmd);
        getCommand("delhome").setTabCompleter(homeCmd);
        getCommand("homes").setExecutor(homeCmd);

        // /buyhome — separated for clarity
        BuyHomeCommand buyCmd = new BuyHomeCommand(this, homeManager);
        getCommand("buyhome").setExecutor(buyCmd);

        getLogger().info("MoroHomes v2 enabled. Default max homes: "
                + getConfig().getInt("default-max-homes", 1));
    }

    public HomeManager getHomeManager() { return homeManager; }
}
