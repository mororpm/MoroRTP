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

/**
 * BountyCommand — handles /bounty command.
 *
 * Subcommands:
 *   /bounty                 — open Browse GUI (page 0, no filter)
 *   /bounty list            — same as above
 *   /bounty list <name>     — open Browse GUI with name filter pre-applied
 *   /bounty create <player> — open Deposit GUI to place a bounty
 *   /bounty info <player>   — print active bounty info in chat
 */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final MoroBounty    plugin;
    private final BountyManager manager;
    private final BountyGUI     gui;

    public BountyCommand(MoroBounty plugin, BountyManager manager, BountyGUI gui) {
        this.plugin  = plugin;
        this.manager = manager;
        this.gui     = gui;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // onCommand
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        // /bounty (no args) — open Browse GUI immediately
        if (args.length == 0) {
            gui.openBrowse(player, 0, null);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ──────────────────────────────────────────────────────────────────
            // /bounty list [name] — Browse GUI, optionally with filter
            // ──────────────────────────────────────────────────────────────────
            case "list" -> {
                String filter = (args.length >= 2) ? args[1] : null;
                gui.openBrowse(player, 0, filter);
            }

            // ──────────────────────────────────────────────────────────────────
            // /bounty create <player> — Deposit GUI
            // ──────────────────────────────────────────────────────────────────
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /bounty create <player>");
                    return true;
                }

                Player victim = Bukkit.getPlayer(args[1]);
                if (victim == null) {
                    player.sendMessage(ChatColor.RED + "[BOUNTY] Player '"
                            + args[1] + "' is offline.");
                    return true;
                }
                if (victim.equals(player)) {
                    player.sendMessage(ChatColor.RED
                            + "[BOUNTY] You cannot place a bounty on yourself.");
                    return true;
                }
                if (manager.hasBounty(victim.getUniqueId())) {
                    String desc = manager.getBountyDescription(victim.getUniqueId());
                    player.sendMessage(ChatColor.RED + "[BOUNTY] "
                            + victim.getName() + " already has an active bounty"
                            + (desc != null ? " (" + desc + ")" : "") + ".");
                    return true;
                }

                gui.openFor(player, victim);
            }

            // ──────────────────────────────────────────────────────────────────
            // /bounty info <player> — chat info (no GUI required)
            // ──────────────────────────────────────────────────────────────────
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /bounty info <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "[BOUNTY] Player '"
                            + args[1] + "' is offline.");
                    return true;
                }
                if (!manager.hasBounty(target.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "[BOUNTY] "
                            + target.getName() + " has no active bounty.");
                    return true;
                }
                String desc = manager.getBountyDescription(target.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "[BOUNTY] "
                        + ChatColor.WHITE + "Bounty on "
                        + ChatColor.RED + target.getName()
                        + ChatColor.WHITE + " — reward: "
                        + ChatColor.GOLD + (desc != null ? desc : "Unknown item"));
            }

            // ──────────────────────────────────────────────────────────────────
            // Unknown subcommand
            // ──────────────────────────────────────────────────────────────────
            default -> sendHelp(player);
        }

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tab Completion
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "create", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                // list <name> — suggest online players as filter
                case "list", "create", "info" -> {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase()
                                    .startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "— BOUNTY —");
        p.sendMessage(ChatColor.GRAY + "/bounty "
                + ChatColor.WHITE + "— Open the full bounty list");
        p.sendMessage(ChatColor.GRAY + "/bounty list [name] "
                + ChatColor.WHITE + "— Open list with player-name filter");
        p.sendMessage(ChatColor.GRAY + "/bounty create <player> "
                + ChatColor.WHITE + "— Place a bounty (opens deposit menu)");
        p.sendMessage(ChatColor.GRAY + "/bounty info <player> "
                + ChatColor.WHITE + "— Show active bounty details");
    }
}
