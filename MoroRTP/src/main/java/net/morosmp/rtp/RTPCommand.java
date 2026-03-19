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
        if (sender instanceof Player player) {
            // FIX: Use the singleton RTPGUI instance from the main class.
            // The old code did `new RTPGUI(plugin).openMenu(player)` which created a
            // brand-new, UN-registered Listener object every time. The InventoryClickEvent
            // handler on that object never fired, making all buttons unclickable.
            plugin.getRtpGui().openMenu(player);
        }
        return true;
    }
}
