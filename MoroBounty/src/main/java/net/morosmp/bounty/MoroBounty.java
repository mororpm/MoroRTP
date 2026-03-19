package net.morosmp.bounty;

import org.bukkit.plugin.java.JavaPlugin;

public class MoroBounty extends JavaPlugin {

    private BountyManager bountyManager;
    private BountyGUI     bountyGUI;

    @Override
    public void onEnable() {
        // Ensure bounties.yml exists on disk (copies the template from resources if absent)
        saveResource("bounties.yml", false);

        bountyManager = new BountyManager(this);
        bountyGUI     = new BountyGUI(this, bountyManager);

        // Register listeners
        getServer().getPluginManager().registerEvents(bountyGUI, this);
        getServer().getPluginManager().registerEvents(new DeathListener(this, bountyManager), this);

        // Register commands
        getCommand("bounty").setExecutor(new BountyCommand(this, bountyManager, bountyGUI));

        getLogger().info("MoroBounty v2 loaded — physical item economy, no Vault.");
    }

    @Override
    public void onDisable() {
        // BountyManager flushes the YAML on every write; nothing extra needed here.
        getLogger().info("MoroBounty disabled.");
    }

    public BountyManager getBountyManager() { return bountyManager; }
    public BountyGUI     getBountyGUI()     { return bountyGUI;     }
}
