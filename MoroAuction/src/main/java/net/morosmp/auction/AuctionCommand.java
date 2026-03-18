package net.morosmp.auction;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AuctionCommand implements CommandExecutor {
    private final MoroAuction plugin;

    public AuctionCommand(MoroAuction plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length == 0) {
            plugin.getAuctionGUI().openGUI(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell") && args.length == 2) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                p.sendMessage(Utils.color("&cYou must hold an item to sell!"));
                return true;
            }

            try {
                double price = Double.parseDouble(args[1]);
                if (price <= 0) { p.sendMessage(Utils.color("&cPrice must be positive!")); return true; }

                plugin.getAuctionManager().listObject(p, item.clone(), price);
                p.getInventory().setItemInMainHand(null);
                
                p.sendMessage(Utils.color(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.listed")
                    .replace("%item%", item.getType().name()).replace("%price%", String.valueOf(price))));
                
            } catch (NumberFormatException e) {
                p.sendMessage(Utils.color("&cInvalid price!"));
            }
        }
        return true;
    }
}