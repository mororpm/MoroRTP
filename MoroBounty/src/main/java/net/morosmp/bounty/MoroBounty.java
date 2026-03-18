package net.morosmp.bounty;
import org.bukkit.Bukkit; import org.bukkit.plugin.java.JavaPlugin;
public class MoroBounty extends JavaPlugin {
    private BountyManager bountyManager; private VaultHook vaultHook;
    @Override public void onEnable() {
        vaultHook = new VaultHook(); if (!vaultHook.setupEconomy()) { getLogger().severe("Vault not found!"); getServer().getPluginManager().disablePlugin(this); return; }
        bountyManager = new BountyManager(this); getCommand("bounty").setExecutor(new BountyCommand(this));
        getServer().getPluginManager().registerEvents(new DeathListener(this), this); getServer().getPluginManager().registerEvents(new BountyGUI(this), this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> bountyManager.processExpiredBounties(), 20L * 60, 20L * 60 * 60);
    }
    public BountyManager getBountyManager() { return bountyManager; } public VaultHook getVaultHook() { return vaultHook; }
}