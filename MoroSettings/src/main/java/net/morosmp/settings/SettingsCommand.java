package net.morosmp.settings;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SettingsCommand implements CommandExecutor {

    private final SettingsPlugin  plugin;
    private final SettingsManager manager;

    public SettingsCommand(SettingsPlugin plugin, SettingsManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /settings.");
            return true;
        }
        SettingsGUI.open(player, manager);
        return true;
    }
}
