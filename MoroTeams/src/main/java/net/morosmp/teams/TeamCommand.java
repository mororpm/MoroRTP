package net.morosmp.teams;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class TeamCommand implements CommandExecutor, TabCompleter {

    /** Physical cost to create a team. */
    private static final int      COST_AMOUNT   = 16;
    private static final Material COST_MATERIAL = Material.DIAMOND;

    private final TeamsPlugin plugin;
    private final TeamManager manager;

    public TeamCommand(TeamsPlugin plugin, TeamManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {

            // /team create <name>
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("\u00a7cUsage: /team create <name>");
                    return true;
                }
                String name = args[1];

                if (manager.isInTeam(player.getUniqueId())) {
                    player.sendMessage("\u00a7c[Teams] You are already in a team. /team leave first.");
                    return true;
                }
                if (manager.teamExists(name)) {
                    player.sendMessage("\u00a7c[Teams] A team named '\u00a7f" + name + "\u00a7c' already exists.");
                    return true;
                }
                if (name.length() > 16 || !name.matches("[a-zA-Z0-9_]+")) {
                    player.sendMessage("\u00a7c[Teams] Team name must be 1-16 alphanumeric characters.");
                    return true;
                }

                // --- Hardcore check: 16 Diamonds in main hand ---
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() != COST_MATERIAL || hand.getAmount() < COST_AMOUNT) {
                    player.sendMessage("\u00a7c[Teams] Creating a team costs \u00a7f" + COST_AMOUNT
                            + " Diamonds\u00a7c held in your main hand.");
                    player.sendMessage("\u00a7c[Teams] You have: \u00a7f"
                            + (hand.getType() == COST_MATERIAL ? hand.getAmount() : 0)
                            + "\u00a7c Diamonds in hand.");
                    return true;
                }

                // Consume exactly COST_AMOUNT diamonds
                if (hand.getAmount() == COST_AMOUNT) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    hand.setAmount(hand.getAmount() - COST_AMOUNT);
                }

                manager.createTeam(name, player.getUniqueId());
                player.sendMessage("\u00a7a[Teams] Team '\u00a7f" + name
                        + "\u00a7a' created! \u00a78(" + COST_AMOUNT + " Diamonds consumed)");
            }

            // /team invite <player>
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("\u00a7cUsage: /team invite <player>");
                    return true;
                }
                String teamName = manager.getTeamOf(player.getUniqueId());
                if (teamName == null) {
                    player.sendMessage("\u00a7c[Teams] You are not in a team.");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("\u00a7c[Teams] Player '\u00a7f" + args[1] + "\u00a7c' is not online.");
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage("\u00a7c[Teams] You cannot invite yourself.");
                    return true;
                }
                if (manager.isInTeam(target.getUniqueId())) {
                    player.sendMessage("\u00a7c[Teams] That player is already in a team.");
                    return true;
                }

                manager.addInvite(target.getUniqueId(), teamName);
                player.sendMessage("\u00a7a[Teams] Invited \u00a7f" + target.getName()
                        + "\u00a7a to team '\u00a7f" + teamName + "\u00a7a'.");
                target.sendMessage("\u00a7a[Teams] \u00a7f" + player.getName()
                        + "\u00a7a invited you to team '\u00a7f" + teamName
                        + "\u00a7a'. Type \u00a7f/team accept \u00a7ato join.");
            }

            // /team accept
            case "accept" -> {
                String invite = manager.getPendingInvite(player.getUniqueId());
                if (invite == null) {
                    player.sendMessage("\u00a7c[Teams] You have no pending invite.");
                    return true;
                }
                if (manager.isInTeam(player.getUniqueId())) {
                    player.sendMessage("\u00a7c[Teams] You are already in a team.");
                    manager.removeInvite(player.getUniqueId());
                    return true;
                }
                if (!manager.teamExists(invite)) {
                    player.sendMessage("\u00a7c[Teams] That team no longer exists.");
                    manager.removeInvite(player.getUniqueId());
                    return true;
                }

                manager.addMember(invite, player.getUniqueId());
                manager.removeInvite(player.getUniqueId());

                for (UUID uid : manager.getMembers(invite)) {
                    Player m = Bukkit.getPlayer(uid);
                    if (m != null)
                        m.sendMessage("\u00a7a[Teams] \u00a7f" + player.getName()
                                + "\u00a7a has joined the team!");
                }
            }

            // /team leave
            case "leave" -> {
                String teamName = manager.getTeamOf(player.getUniqueId());
                if (teamName == null) {
                    player.sendMessage("\u00a7c[Teams] You are not in a team.");
                    return true;
                }
                // Notify others before removing
                for (UUID uid : manager.getMembers(teamName)) {
                    if (uid.equals(player.getUniqueId())) continue;
                    Player m = Bukkit.getPlayer(uid);
                    if (m != null)
                        m.sendMessage("\u00a7c[Teams] \u00a7f" + player.getName()
                                + "\u00a7c has left the team."
                                + (manager.isOwner(teamName, player.getUniqueId())
                                ? " \u00a78(Ownership transferred)" : ""));
                }
                manager.removeMember(teamName, player.getUniqueId());
                player.sendMessage("\u00a7a[Teams] You have left the team.");
            }

            // /team chat <message>
            case "chat" -> {
                if (args.length < 2) {
                    player.sendMessage("\u00a7cUsage: /team chat <message>");
                    return true;
                }
                String teamName = manager.getTeamOf(player.getUniqueId());
                if (teamName == null) {
                    player.sendMessage("\u00a7c[Teams] You are not in a team.");
                    return true;
                }
                String msg = String.join(" ",
                        Arrays.copyOfRange(args, 1, args.length));
                String formatted = "\u00a7b[" + teamName + "] \u00a7f"
                        + player.getName() + "\u00a77: " + msg;

                for (UUID uid : manager.getMembers(teamName)) {
                    Player m = Bukkit.getPlayer(uid);
                    if (m != null) m.sendMessage(formatted);
                }
            }

            default -> sendHelp(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1)
            return List.of("create","invite","accept","leave","chat").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("invite"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        return List.of();
    }

    private void sendHelp(Player p) {
        p.sendMessage("\u00a76\u00a7l--- MoroTeams ---");
        p.sendMessage("\u00a7e/team create <name>    \u00a77\u2014 Create (costs 16 Diamonds)");
        p.sendMessage("\u00a7e/team invite <player>  \u00a77\u2014 Invite a player");
        p.sendMessage("\u00a7e/team accept           \u00a77\u2014 Accept an invite");
        p.sendMessage("\u00a7e/team leave            \u00a77\u2014 Leave your team");
        p.sendMessage("\u00a7e/team chat <msg>       \u00a77\u2014 Team-only chat");
    }
}
