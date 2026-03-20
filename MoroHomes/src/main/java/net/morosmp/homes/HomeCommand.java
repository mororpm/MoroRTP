package net.morosmp.homes;

import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HomeCommand — handles /sethome, /home, /delhome, /homes.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  /sethome <n>                                                          │
 * │    Sets (or overwrites) a named home at the player's current position.  │
 * │    Name must be 1-16 alphanumeric characters.                           │
 * │    If the player is at their slot limit, refuse and suggest /buyhome.   │
 * │    Overwriting an EXISTING home name does NOT consume a new slot.       │
 * │                                                                         │
 * │  /home [name]                                                           │
 * │    Teleports to the named home.                                         │
 * │    If no name is given and the player has exactly 1 home, uses it.      │
 * │    If no name is given and the player has 0 or 2+ homes, lists usage.   │
 * │                                                                         │
 * │  /delhome <n>                                                          │
 * │    Deletes a specific named home.                                       │
 * │    Tab-completes from the player's existing home names.                 │
 * │                                                                         │
 * │  /homes                                                                 │
 * │    Lists all the player's homes with their slot usage.                  │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
public class HomeCommand implements CommandExecutor, TabCompleter {

    private final HomesPlugin plugin;
    private final HomeManager manager;

    public HomeCommand(HomesPlugin plugin, HomeManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // =========================================================================
    // Command dispatch
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "sethome" -> handleSetHome(player, args);
            case "home"    -> handleHome(player, args);
            case "delhome" -> handleDelHome(player, args);
            case "homes"   -> handleListHomes(player);
        }
        return true;
    }

    // =========================================================================
    // /sethome <n>
    // =========================================================================

    private void handleSetHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§c[HOMES] Usage: /sethome <name>");
            player.sendMessage("§8Example: /sethome home | /sethome base | /sethome farm");
            return;
        }

        String name = args[0].toLowerCase();

        // Validate name format: 1-16 alphanumeric + underscores
        if (name.length() > 16 || !name.matches("[a-z0-9_]+")) {
            player.sendMessage("§c[HOMES] Home name can only contain letters, numbers, and '_' (max 16 chars).");
            return;
        }

        boolean isOverwrite = manager.hasHome(player.getUniqueId(), name);

        // Slot-limit check: only block if the player is setting a NEW home
        // (overwriting an existing name always costs nothing)
        if (!isOverwrite) {
            int count = manager.getHomeCount(player.getUniqueId());
            int max   = manager.getMaxHomes(player.getUniqueId());
            if (count >= max) {
                // Exact message specified in the task
                player.sendMessage("§c[HOMES] Home limit reached! Buy a new slot: /buyhome");
                // Show their current status for convenience
                player.sendMessage("§8Used: §f" + count + "§8/§f" + max
                        + " §8slots. Use §f/homes §8to view them.");
                return;
            }
        }

        manager.setHome(player.getUniqueId(), name, player.getLocation());

        if (isOverwrite) {
            player.sendMessage("§a[HOMES] Home §f'" + name + "' §ahas been updated.");
        } else {
            int count = manager.getHomeCount(player.getUniqueId());
            int max   = manager.getMaxHomes(player.getUniqueId());
            player.sendMessage("§a[HOMES] Home §f'" + name + "' §ahas been set. "
                    + "§8[§f" + count + "§8/§f" + max + "§8 slots]");
        }
    }

    // =========================================================================
    // /home [name]
    // =========================================================================

    private void handleHome(Player player, String[] args) {
        // No name provided → smart default
        if (args.length < 1) {
            List<String> names = manager.getHomeNames(player.getUniqueId());
            if (names.isEmpty()) {
                player.sendMessage("§c[HOMES] You have no homes. Set one with §f/sethome <name>§c.");
                return;
            }
            if (names.size() == 1) {
                // Exactly one home — use it silently
                teleportToHome(player, names.get(0));
            } else {
                // Multiple homes — must specify a name
                player.sendMessage("§c[HOMES] Specify a home name: §f/home <name>");
                player.sendMessage("§7Your homes: §f" + String.join("§7, §f", names));
            }
            return;
        }

        teleportToHome(player, args[0]);
    }

    /** Shared teleport logic with full null/world-missing checks. */
    private void teleportToHome(Player player, String name) {
        name = name.toLowerCase();
        if (!manager.hasHome(player.getUniqueId(), name)) {
            player.sendMessage("§c[HOMES] Home §f'" + name + "' §cnot found.");
            List<String> names = manager.getHomeNames(player.getUniqueId());
            if (!names.isEmpty()) {
                player.sendMessage("§7Your homes: §f" + String.join("§7, §f", names));
            }
            return;
        }

        Location loc = manager.getHome(player.getUniqueId(), name);
        if (loc == null) {
            player.sendMessage("§c[HOMES] The world containing home §f'" + name + "' §cno longer exists.");
            return;
        }

        player.teleport(loc);
        player.sendMessage("§a[HOMES] Teleporting to home §f'" + name + "'§a.");
    }

    // =========================================================================
    // /delhome <n>
    // =========================================================================

    private void handleDelHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§c[HOMES] Usage: §f/delhome <name>");
            List<String> names = manager.getHomeNames(player.getUniqueId());
            if (!names.isEmpty()) {
                player.sendMessage("§7Your homes: §f" + String.join("§7, §f", names));
            }
            return;
        }

        String name = args[0].toLowerCase();

        if (!manager.hasHome(player.getUniqueId(), name)) {
            player.sendMessage("§c[HOMES] Home §f'" + name + "' §cnot found.");
            return;
        }

        manager.deleteHome(player.getUniqueId(), name);
        player.sendMessage("§a[HOMES] Home §f'" + name + "' §ahas been deleted.");

        int remaining = manager.getHomeCount(player.getUniqueId());
        int max       = manager.getMaxHomes(player.getUniqueId());
        player.sendMessage("§8Homes remaining: §f" + remaining + "§8/§f" + max + "§8 slots.");
    }

    // =========================================================================
    // /homes
    // =========================================================================

    private void handleListHomes(Player player) {
        List<String> names = manager.getHomeNames(player.getUniqueId());
        int max = manager.getMaxHomes(player.getUniqueId());

        player.sendMessage("§6— YOUR HOMES §8[§f" + names.size() + "§8/§f" + max + "§8 SLOTS] —");

        if (names.isEmpty()) {
            player.sendMessage("§7  No homes set. Use §f/sethome <name>§7.");
        } else {
            for (String n : names) {
                Location loc = manager.getHome(player.getUniqueId(), n);
                String coords = (loc != null)
                        ? "§8X:§f" + (int) loc.getX()
                            + " §8Y:§f" + (int) loc.getY()
                            + " §8Z:§f" + (int) loc.getZ()
                            + " §8[" + loc.getWorld().getName() + "]"
                        : "§c(world unavailable)";
                player.sendMessage("  §e" + n + " §8→ " + coords);
            }
        }

        // Upgrade hint if another slot is available
        int upgradeCost = manager.getUpgradeCost(player.getUniqueId());
        if (upgradeCost > 0) {
            player.sendMessage("§8Next slot: §f/buyhome §8(cost: §f"
                    + upgradeCost + " §bDiamonds§8)");
        } else if (names.size() < max) {
            // Has free slots remaining
            player.sendMessage("§7  Free slots: §f" + (max - names.size()));
        } else {
            player.sendMessage("§8All slots are fully upgraded.");
        }
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length != 1) return List.of();

        // For /home and /delhome, complete from the player's existing home names
        String sub = command.getName().toLowerCase();
        if (sub.equals("home") || sub.equals("delhome")) {
            return manager.getHomeNames(player.getUniqueId()).stream()
                    .filter(n -> n.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // /sethome — tab-complete existing names (for easy overwrite) or suggest new
        if (sub.equals("sethome")) {
            return manager.getHomeNames(player.getUniqueId()).stream()
                    .filter(n -> n.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
