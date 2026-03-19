package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private final MoroBounty    plugin;
    private final BountyManager manager;
    private final BountyGUI     gui;

    public BountyCommand(MoroBounty plugin, BountyManager manager, BountyGUI gui) {
        this.plugin  = plugin;
        this.manager = manager;
        this.gui     = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player sponsor)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sponsor);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ------------------------------------------------------------------
            // /bounty create <player>
            // ------------------------------------------------------------------
            case "create" -> {
                if (args.length < 2) {
                    sponsor.sendMessage(ChatColor.RED + "Usage: /bounty create <player>");
                    return true;
                }

                Player victim = Bukkit.getPlayer(args[1]);
                if (victim == null) {
                    sponsor.sendMessage(ChatColor.RED + "[MoroBounty] Player '" + args[1] + "' is not online.");
                    return true;
                }
                if (victim.equals(sponsor)) {
                    sponsor.sendMessage(ChatColor.RED + "[MoroBounty] You cannot place a bounty on yourself.");
                    return true;
                }
                if (manager.hasBounty(victim.getUniqueId())) {
                    String desc = manager.getBountyDescription(victim.getUniqueId());
                    sponsor.sendMessage(ChatColor.RED + "[MoroBounty] " + victim.getName()
                            + " already has an active bounty" + (desc != null ? " (" + desc + ")" : "") + ".");
                    return true;
                }

                gui.openFor(sponsor, victim);
            }

            // ------------------------------------------------------------------
            // /bounty info <player>
            // ------------------------------------------------------------------
            case "info" -> {
                if (args.length < 2) {
                    sponsor.sendMessage(ChatColor.RED + "Usage: /bounty info <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sponsor.sendMessage(ChatColor.RED + "[MoroBounty] Player '" + args[1] + "' is not online.");
                    return true;
                }
                if (!manager.hasBounty(target.getUniqueId())) {
                    sponsor.sendMessage(ChatColor.YELLOW + "[MoroBounty] " + target.getName() + " has no active bounty.");
                    return true;
                }
                String desc = manager.getBountyDescription(target.getUniqueId());
                sponsor.sendMessage(ChatColor.GREEN + "[MoroBounty] " + ChatColor.WHITE
                        + target.getName() + "'s bounty reward: "
                        + ChatColor.GOLD + (desc != null ? desc : "Unknown item"));
            }

            // ------------------------------------------------------------------
            // Unrecognised sub-command
            // ------------------------------------------------------------------
            default -> sendHelp(sponsor);
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("info"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "— MoroBounty Commands —");
        p.sendMessage(ChatColor.GRAY + "/bounty create <player> " + ChatColor.WHITE + "— Open deposit GUI to place a bounty");
        p.sendMessage(ChatColor.GRAY + "/bounty info <player>   " + ChatColor.WHITE + "— Check a player's active bounty");
    }
}
