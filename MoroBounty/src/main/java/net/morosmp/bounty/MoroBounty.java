package net.morosmp.bounty;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MoroBounty v3 — physical item bounty system.
 *
 * Storage backend migrated from bounties.yml to SQLite in v3.
 * The external Python API (API.py) can now read the same database
 * file concurrently without file-locking conflicts (WAL mode).
 */
public class MoroBounty extends JavaPlugin {

    private BountyManager bountyManager;
    private BountyGUI     bountyGUI;

    @Override
    public void onEnable() {
        // No YAML resource to copy — storage is SQLite only.
        // BountyManager.initDatabase() creates the file and table on first run.
        bountyManager = new BountyManager(this);
        bountyGUI     = new BountyGUI(this, bountyManager);

        getServer().getPluginManager().registerEvents(bountyGUI, this);
        getServer().getPluginManager().registerEvents(new DeathListener(this, bountyManager), this);

        getCommand("bounty").setExecutor(new BountyCommand(this, bountyManager, bountyGUI));
        getCommand("bounty").setTabCompleter(new BountyCommand(this, bountyManager, bountyGUI));

        getLogger().info("MoroBounty v3 enabled — SQLite backend, physical item economy.");
    }

    @Override
    public void onDisable() {
        // Close the SQLite connection gracefully so WAL frames are checkpointed
        // and the .db file is in a clean state for the Python API to read.
        if (bountyManager != null) {
            bountyManager.closeConnection();
        }
        getLogger().info("MoroBounty disabled — database connection closed.");
    }

    public BountyManager getBountyManager() { return bountyManager; }
    public BountyGUI     getBountyGUI()     { return bountyGUI;     }
}
