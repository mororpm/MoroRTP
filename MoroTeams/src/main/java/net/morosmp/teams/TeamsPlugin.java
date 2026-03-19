package net.morosmp.teams;

import org.bukkit.plugin.java.JavaPlugin;

public class TeamsPlugin extends JavaPlugin {

    private TeamManager teamManager;

    @Override
    public void onEnable() {
        saveResource("teams.yml", false);
        teamManager = new TeamManager(this);

        TeamCommand cmd = new TeamCommand(this, teamManager);
        getCommand("team").setExecutor(cmd);
        getCommand("team").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new TeamListener(teamManager), this);
        getLogger().info("MoroTeams enabled.");
    }

    public TeamManager getTeamManager() { return teamManager; }
}
