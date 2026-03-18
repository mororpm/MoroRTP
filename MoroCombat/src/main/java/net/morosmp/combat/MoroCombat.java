package net.morosmp.combat;
import org.bukkit.plugin.java.JavaPlugin;
public class MoroCombat extends JavaPlugin {
    private CombatManager combatManager;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        combatManager = new CombatManager(this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, combatManager), this);
    }
}