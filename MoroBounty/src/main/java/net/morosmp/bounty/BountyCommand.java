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
 * BountyCommand — обрабатывает команду /bounty.
 *
 * Подкоманды:
 *   /bounty              — открыть Browse GUI (страница 0, без фильтра)
 *   /bounty list         — то же самое
 *   /bounty list <ник>   — открыть Browse GUI с предустановленным фильтром по нику
 *   /bounty create <игрок> — открыть Deposit GUI для установки баунти
 *   /bounty info <игрок>   — показать информацию об активном баунти в чат
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
            sender.sendMessage(ChatColor.RED + "Только игроки могут использовать эту команду.");
            return true;
        }

        // /bounty (без аргументов) — сразу открываем Browse GUI
        if (args.length == 0) {
            gui.openBrowse(player, 0, null);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ──────────────────────────────────────────────────────────────────
            // /bounty list [ник]   — Browse GUI, опционально с фильтром
            // ──────────────────────────────────────────────────────────────────
            case "list" -> {
                String filter = (args.length >= 2) ? args[1] : null;
                gui.openBrowse(player, 0, filter);
            }

            // ──────────────────────────────────────────────────────────────────
            // /bounty create <игрок>   — Deposit GUI
            // ──────────────────────────────────────────────────────────────────
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Использование: /bounty create <игрок>");
                    return true;
                }

                Player victim = Bukkit.getPlayer(args[1]);
                if (victim == null) {
                    player.sendMessage(ChatColor.RED + "[MoroBounty] Игрок '"
                            + args[1] + "' не в сети.");
                    return true;
                }
                if (victim.equals(player)) {
                    player.sendMessage(ChatColor.RED
                            + "[MoroBounty] Нельзя установить баунти на себя.");
                    return true;
                }
                if (manager.hasBounty(victim.getUniqueId())) {
                    String desc = manager.getBountyDescription(victim.getUniqueId());
                    player.sendMessage(ChatColor.RED + "[MoroBounty] На "
                            + victim.getName() + " уже есть активный баунти"
                            + (desc != null ? " (" + desc + ")" : "") + ".");
                    return true;
                }

                gui.openFor(player, victim);
            }

            // ──────────────────────────────────────────────────────────────────
            // /bounty info <игрок>   — инфо в чат (не требует GUI)
            // ──────────────────────────────────────────────────────────────────
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Использование: /bounty info <игрок>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "[MoroBounty] Игрок '"
                            + args[1] + "' не в сети.");
                    return true;
                }
                if (!manager.hasBounty(target.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "[MoroBounty] У "
                            + target.getName() + " нет активного баунти.");
                    return true;
                }
                String desc = manager.getBountyDescription(target.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "[MoroBounty] "
                        + ChatColor.WHITE + "Баунти на "
                        + ChatColor.RED + target.getName()
                        + ChatColor.WHITE + " — награда: "
                        + ChatColor.GOLD + (desc != null ? desc : "Неизвестный предмет"));
            }

            // ──────────────────────────────────────────────────────────────────
            // Неизвестная подкоманда
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
                // list <ник> — предлагаем онлайн игроков как фильтр
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
        p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "— MoroBounty —");
        p.sendMessage(ChatColor.GRAY + "/bounty "
                + ChatColor.WHITE + "— Открыть список всех баунти");
        p.sendMessage(ChatColor.GRAY + "/bounty list [ник] "
                + ChatColor.WHITE + "— Список с фильтром по нику");
        p.sendMessage(ChatColor.GRAY + "/bounty create <игрок> "
                + ChatColor.WHITE + "— Установить баунти (открывает меню депозита)");
        p.sendMessage(ChatColor.GRAY + "/bounty info <игрок> "
                + ChatColor.WHITE + "— Информация об активном баунти");
    }
}
