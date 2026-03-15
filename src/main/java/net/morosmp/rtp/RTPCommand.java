package net.morosmp.rtp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RTPCommand implements CommandExecutor {
    private final MoroRTP plugin;

    public RTPCommand(MoroRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfig().getString("messages.only-players"));
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            new RTPGui(plugin).open(player);
        } else {
            player.sendMessage(plugin.getConfigManager().parseMessage("already-teleporting")); // Позже можно добавить /rtp reload
        }
        
        return true;
    }
}
