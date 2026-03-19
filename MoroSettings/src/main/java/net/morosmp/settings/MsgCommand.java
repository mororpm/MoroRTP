package net.morosmp.settings;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MsgCommand — handles /msg and /r, gated by per-player PM settings.
 *
 * /msg <player> <message>  — send a direct message
 * /r <message>             — reply to last conversation partner
 *
 * Both the sender's AND recipient's pm-enabled flag are checked.
 */
public class MsgCommand implements CommandExecutor, TabCompleter {

    private final SettingsPlugin  plugin;
    private final SettingsManager manager;

    public MsgCommand(SettingsPlugin plugin, SettingsManager manager) {
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

        switch (command.getName().toLowerCase()) {

            case "msg" -> {
                if (args.length < 2) {
                    player.sendMessage("\u00a7cUsage: /msg <player> <message>");
                    return true;
                }

                // Sender must have PMs enabled
                if (!manager.isPmEnabled(player.getUniqueId())) {
                    player.sendMessage("\u00a7c[MSG] You have private messages disabled.");
                    player.sendMessage("\u00a7c[MSG] Enable them in \u00a7f/settings\u00a7c.");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage("\u00a7c[MSG] Player '\u00a7f" + args[0]
                            + "\u00a7c' is not online.");
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage("\u00a7c[MSG] You can\u2019t message yourself.");
                    return true;
                }

                // Recipient must also have PMs enabled
                if (!manager.isPmEnabled(target.getUniqueId())) {
                    player.sendMessage("\u00a7c[MSG] \u00a7f" + target.getName()
                            + "\u00a7c has private messages disabled.");
                    return true;
                }

                String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String outgoing = "\u00a77[\u00a7fYou \u00a77\u2192 \u00a7f"
                        + target.getName() + "\u00a77] \u00a7f" + msg;
                String incoming = "\u00a77[\u00a7f" + player.getName()
                        + " \u00a77\u2192 \u00a7fYou\u00a77] \u00a7f" + msg;

                player.sendMessage(outgoing);
                target.sendMessage(incoming);

                // Update both parties' last-messaged reference
                plugin.getLastMessaged().put(player.getUniqueId(), target.getUniqueId());
                plugin.getLastMessaged().put(target.getUniqueId(), player.getUniqueId());
            }

            case "r" -> {
                if (args.length < 1) {
                    player.sendMessage("\u00a7cUsage: /r <message>");
                    return true;
                }
                if (!manager.isPmEnabled(player.getUniqueId())) {
                    player.sendMessage("\u00a7c[MSG] You have private messages disabled.");
                    return true;
                }

                UUID lastUuid = plugin.getLastMessaged().get(player.getUniqueId());
                if (lastUuid == null) {
                    player.sendMessage("\u00a7c[MSG] No one to reply to.");
                    return true;
                }

                Player target = Bukkit.getPlayer(lastUuid);
                if (target == null) {
                    player.sendMessage("\u00a7c[MSG] Your last conversation partner is no longer online.");
                    return true;
                }
                if (!manager.isPmEnabled(target.getUniqueId())) {
                    player.sendMessage("\u00a7c[MSG] \u00a7f" + target.getName()
                            + "\u00a7c has private messages disabled.");
                    return true;
                }

                String msg = String.join(" ", args);
                player.sendMessage("\u00a77[\u00a7fYou \u00a77\u2192 \u00a7f"
                        + target.getName() + "\u00a77] \u00a7f" + msg);
                target.sendMessage("\u00a77[\u00a7f" + player.getName()
                        + " \u00a77\u2192 \u00a7fYou\u00a77] \u00a7f" + msg);

                plugin.getLastMessaged().put(player.getUniqueId(), target.getUniqueId());
                plugin.getLastMessaged().put(target.getUniqueId(), player.getUniqueId());
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("msg") && args.length == 1)
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        return List.of();
    }
}
