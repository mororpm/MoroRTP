package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BountyCommand implements CommandExecutor {
    private final MoroBounty plugin;
    public BountyCommand(MoroBounty plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        
        if (args.length == 0) { new BountyGUI(plugin).openGUI(p, 0); return true; }
        
        if (args.length == 2) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { 
                p.sendMessage(Utils.color(plugin.getConfig().getString("messages.player-not-found"), true)); 
                return true; 
            }
            
            try {
                // Поддержка 1k, 1m и т.д.
                double amount = Utils.parseAmount(args[1]);
                double min = plugin.getBountyManager().getMinBountyFor(target.getUniqueId());
                
                if (amount < min) { 
                    p.sendMessage(Utils.color(plugin.getConfig().getString("messages.min-amount").replace("%amount%", String.format("%.0f", min)), true)); 
                    return true; 
                }
                
                if (plugin.getVaultHook().hasEnough(p, amount)) {
                    plugin.getVaultHook().withdraw(p, amount); 
                    plugin.getBountyManager().addSmartContract(p.getUniqueId(), target.getUniqueId(), amount);
                    
                    String msg = plugin.getConfig().getString("messages.bounty-added")
                        .replace("%prefix%", plugin.getConfig().getString("prefix"))
                        .replace("%player%", p.getName())
                        .replace("%amount%", String.format("%.0f", amount))
                        .replace("%target%", target.getName());
                    Bukkit.broadcastMessage(Utils.color(msg, true));
                } else {
                    p.sendMessage(Utils.color(plugin.getConfig().getString("messages.insufficient-funds"), true));
                }
            } catch (Exception e) { 
                p.sendMessage(Utils.color(plugin.getConfig().getString("messages.invalid-amount"), true)); 
            }
        }
        return true;
    }
}