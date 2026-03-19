package net.morosmp.warp;

import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class WarpCommand implements CommandExecutor, TabCompleter {

    private final WarpPlugin plugin;
    private final WarpManager manager;

    public WarpCommand(WarpPlugin plugin, WarpManager manager) {
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

            case "setwarp" -> {
                if (!player.hasPermission("morowarp.admin")) {
                    player.sendMessage("\u00a7c[Warp] You don't have permission to set warps.");
                    return true;
                }
                if (args.length < 1) {
                    player.sendMessage("\u00a7cUsage: /setwarp <name>");
                    return true;
                }
                String name = args[0].toLowerCase();
                if (!name.matches("[a-zA-Z0-9_]+") || name.length() > 24) {
                    player.sendMessage("\u00a7c[Warp] Warp name must be alphanumeric, max 24 chars.");
                    return true;
                }
                boolean update = manager.warpExists(name);
                manager.setWarp(name, player.getLocation());
                player.sendMessage("\u00a7a[Warp] Warp '\u00a7f" + name + "\u00a7a' "
                        + (update ? "updated" : "created") + " at your location.");
            }

            case "warp" -> {
                if (args.length < 1) {
                    List<String> names = manager.getWarpNames();
                    if (names.isEmpty()) {
                        player.sendMessage("\u00a7c[Warp] No warps have been set yet.");
                    } else {
                        player.sendMessage("\u00a76[Warp] \u00a7fAvailable warps: \u00a7e"
                                + String.join("\u00a77, \u00a7e", names));
                    }
                    return true;
                }
                String name = args[0].toLowerCase();
                if (!manager.warpExists(name)) {
                    player.sendMessage("\u00a7c[Warp] Warp '\u00a7f" + name
                            + "\u00a7c' does not exist. Use \u00a7f/warp\u00a7c to list all.");
                    return true;
                }
                Location loc = manager.getWarp(name);
                if (loc == null) {
                    player.sendMessage("\u00a7c[Warp] That warp's world no longer exists!");
                    return true;
                }
                player.teleport(loc);
                player.sendMessage("\u00a7a[Warp] Teleported to '\u00a7f" + name + "\u00a7a'.");
            }

            case "delwarp" -> {
                if (!player.hasPermission("morowarp.admin")) {
                    player.sendMessage("\u00a7c[Warp] You don't have permission to delete warps.");
                    return true;
                }
                if (args.length < 1) {
                    player.sendMessage("\u00a7cUsage: /delwarp <name>");
                    return true;
                }
                String name = args[0].toLowerCase();
                if (!manager.warpExists(name)) {
                    player.sendMessage("\u00a7c[Warp] Warp '\u00a7f" + name + "\u00a7c' does not exist.");
                    return true;
                }
                manager.deleteWarp(name);
                player.sendMessage("\u00a7a[Warp] Warp '\u00a7f" + name + "\u00a7a' deleted.");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1)
            return manager.getWarpNames().stream()
                    .filter(n -> n.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        return List.of();
    }
}
