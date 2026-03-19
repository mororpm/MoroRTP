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
            player.sendMessage("§c[Homes] Использование: /sethome <название>");
            player.sendMessage("§8Пример: /sethome home | /sethome base | /sethome farm");
            return;
        }

        String name = args[0].toLowerCase();

        // Validate name format: 1-16 alphanumeric + underscores
        if (name.length() > 16 || !name.matches("[a-z0-9_]+")) {
            player.sendMessage("§c[Homes] Название дома может содержать только буквы, цифры и '_' (макс. 16 символов).");
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
                player.sendMessage("§cЛимит домов! Купите новый слот: /buyhome");
                // Show their current status for convenience
                player.sendMessage("§8Занято: §f" + count + "§8/§f" + max
                        + " §8слотов. Используйте §f/homes §8для просмотра.");
                return;
            }
        }

        manager.setHome(player.getUniqueId(), name, player.getLocation());

        if (isOverwrite) {
            player.sendMessage("§a[Homes] Дом §f'" + name + "' §aобновлён.");
        } else {
            int count = manager.getHomeCount(player.getUniqueId());
            int max   = manager.getMaxHomes(player.getUniqueId());
            player.sendMessage("§a[Homes] Дом §f'" + name + "' §aустановлен. "
                    + "§8[§f" + count + "§8/§f" + max + "§8 слотов]");
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
                player.sendMessage("§c[Homes] У вас нет домов. Установите один командой §f/sethome <название>§c.");
                return;
            }
            if (names.size() == 1) {
                // Exactly one home — use it silently
                teleportToHome(player, names.get(0));
            } else {
                // Multiple homes — must specify a name
                player.sendMessage("§c[Homes] Укажите название дома: §f/home <название>");
                player.sendMessage("§7Ваши дома: §f" + String.join("§7, §f", names));
            }
            return;
        }

        teleportToHome(player, args[0]);
    }

    /** Shared teleport logic with full null/world-missing checks. */
    private void teleportToHome(Player player, String name) {
        name = name.toLowerCase();
        if (!manager.hasHome(player.getUniqueId(), name)) {
            player.sendMessage("§c[Homes] Дом §f'" + name + "' §cне найден.");
            List<String> names = manager.getHomeNames(player.getUniqueId());
            if (!names.isEmpty()) {
                player.sendMessage("§7Ваши дома: §f" + String.join("§7, §f", names));
            }
            return;
        }

        Location loc = manager.getHome(player.getUniqueId(), name);
        if (loc == null) {
            player.sendMessage("§c[Homes] Мир, в котором находится дом §f'" + name + "', §cбольше не существует.");
            return;
        }

        player.teleport(loc);
        player.sendMessage("§a[Homes] Телепортация к дому §f'" + name + "'§a.");
    }

    // =========================================================================
    // /delhome <n>
    // =========================================================================

    private void handleDelHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§c[Homes] Использование: §f/delhome <название>");
            List<String> names = manager.getHomeNames(player.getUniqueId());
            if (!names.isEmpty()) {
                player.sendMessage("§7Ваши дома: §f" + String.join("§7, §f", names));
            }
            return;
        }

        String name = args[0].toLowerCase();

        if (!manager.hasHome(player.getUniqueId(), name)) {
            player.sendMessage("§c[Homes] Дом §f'" + name + "' §cне найден.");
            return;
        }

        manager.deleteHome(player.getUniqueId(), name);
        player.sendMessage("§a[Homes] Дом §f'" + name + "' §aудалён.");

        int remaining = manager.getHomeCount(player.getUniqueId());
        int max       = manager.getMaxHomes(player.getUniqueId());
        player.sendMessage("§8Осталось домов: §f" + remaining + "§8/§f" + max + "§8 слотов.");
    }

    // =========================================================================
    // /homes
    // =========================================================================

    private void handleListHomes(Player player) {
        List<String> names = manager.getHomeNames(player.getUniqueId());
        int max = manager.getMaxHomes(player.getUniqueId());

        player.sendMessage("§6§l— Ваши дома §8[§f" + names.size() + "§8/§f" + max + "§8 слотов] —");

        if (names.isEmpty()) {
            player.sendMessage("§7  Нет установленных домов. Используйте §f/sethome <название>§7.");
        } else {
            for (String n : names) {
                Location loc = manager.getHome(player.getUniqueId(), n);
                String coords = (loc != null)
                        ? "§8X:§f" + (int) loc.getX()
                            + " §8Y:§f" + (int) loc.getY()
                            + " §8Z:§f" + (int) loc.getZ()
                            + " §8[" + loc.getWorld().getName() + "]"
                        : "§c(мир недоступен)";
                player.sendMessage("  §e" + n + " §8→ " + coords);
            }
        }

        // Upgrade hint if another slot is available
        int upgradeCost = manager.getUpgradeCost(player.getUniqueId());
        if (upgradeCost > 0) {
            player.sendMessage("§8Следующий слот: §f/buyhome §8(стоимость: §f"
                    + upgradeCost + " §bАлмазов§8)");
        } else if (names.size() < max) {
            // Has free slots remaining
            player.sendMessage("§7  Свободных слотов: §f" + (max - names.size()));
        } else {
            player.sendMessage("§8Все слоты максимально прокачаны.");
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
