package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BountyGUI — управляет тремя типами инвентарей:
 *
 *  1. BROWSE GUI (54 слота, Double Chest)
 *     ┌─────────────────────────────────────────────┐
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │  ← Ряды 0–4 (слоты 0–44)
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │    По 9 голов на ряд = 45/страницу
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │
 *     │  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD  HEAD   │
 *     │ ←НАЗАД  GL  ПОИСК  GL  СТР   GL  GL  GL →  │  ← Ряд 5 (слоты 45–53): навигация
 *     └─────────────────────────────────────────────┘
 *     Слот 45 = ← Назад   (только если page > 0)
 *     Слот 47 = [ПОИСК]   (всегда)
 *     Слот 49 = Страница X/Y (всегда)
 *     Слот 53 = Вперёд → (только если есть следующая страница)
 *
 *  2. DEPOSIT GUI (9 слотов, Single Chest)
 *     Открывается через /bounty create <player>. Логика из v2 без изменений.
 *     Слот 4 = депозит, Слот 7 = подтвердить, Слот 8 = отмена.
 *
 *  3. ANVIL GUI (поиск)
 *     Открывается кликом на [ПОИСК] внутри Browse GUI.
 *     Игрок вводит ник → нажимает на вывод (слот 2) или закрывает анвил →
 *     Browse GUI переоткрывается с применённым фильтром.
 *
 * Singleton: один экземпляр BountyGUI регистрируется как Listener в MoroBounty.java.
 */
public class BountyGUI implements Listener {

    // ═══════════════════════════════════════════════════════════════════════════
    // Константы заголовков (используем stripColor для сравнения → безопасно)
    // ═══════════════════════════════════════════════════════════════════════════

    static final String BROWSE_TITLE  = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "☠ Активные Баунти";
    static final String DEPOSIT_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Установить Баунти";

    // ═══════════════════════════════════════════════════════════════════════════
    // Слоты Deposit GUI (9-slot)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int DEPOSIT_SLOT  = 4;
    private static final int CONFIRM_SLOT  = 7;
    private static final int CANCEL_SLOT   = 8;

    // ═══════════════════════════════════════════════════════════════════════════
    // Слоты Browse GUI (54-slot, навигационный ряд 45–53)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int SLOT_PREV         = 45;
    private static final int SLOT_SEARCH       = 47;
    private static final int SLOT_PAGE_INFO    = 49;
    private static final int SLOT_NEXT         = 53;
    private static final int ITEMS_PER_PAGE    = 45;   // слоты 0–44

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final MoroBounty    plugin;
    private final BountyManager manager;

    /** sponsor UUID → victim UUID (для Deposit GUI) */
    private final Map<UUID, UUID>    depositSessions = new HashMap<>();

    /** player UUID → текущая страница (для Browse GUI) */
    private final Map<UUID, Integer> pageMap         = new HashMap<>();

    /** player UUID → активный фильтр поиска (null = без фильтра) */
    private final Map<UUID, String>  filterMap       = new HashMap<>();

    /** игроки, у которых открыт Anvil для поиска */
    private final Set<UUID>          searchSessions  = new HashSet<>();

    public BountyGUI(MoroBounty plugin, BountyManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC OPEN METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Открывает Browse GUI для игрока.
     *
     * @param player  кто открывает
     * @param page    0-индексированный номер страницы
     * @param filter  строка-фильтр по нику (null или "" = показать всё)
     */
    public void openBrowse(Player player, int page, String filter) {
        // 1. Получаем список всех жертв с баунти
        List<UUID> victims = manager.getAllBountyVictimUuids();

        // 2. Применяем фильтр поиска
        final boolean hasFilter = filter != null && !filter.isBlank();
        if (hasFilter) {
            final String lf = filter.toLowerCase();
            victims = victims.stream().filter(uuid -> {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                return op.getName() != null && op.getName().toLowerCase().contains(lf);
            }).collect(Collectors.toList());
        }

        // 3. Вычисляем страницу / количество страниц
        int totalPages = Math.max(1, (int) Math.ceil(victims.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        // 4. Сохраняем состояние для навигации
        pageMap.put(player.getUniqueId(), page);
        if (hasFilter) filterMap.put(player.getUniqueId(), filter);
        else           filterMap.remove(player.getUniqueId());

        // 5. Строим инвентарь
        Inventory inv = Bukkit.createInventory(null, 54, BROWSE_TITLE);

        // Заполняем слоты голов (0–44)
        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, victims.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildHeadItem(victims.get(i)));
        }

        // Заполняем навигационный ряд стеклом
        ItemStack navGlass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < 54; s++) inv.setItem(s, navGlass);

        // ← Назад
        if (page > 0) {
            inv.setItem(SLOT_PREV, makeItem(Material.ARROW,
                    ChatColor.YELLOW + "← Назад",
                    ChatColor.GRAY + "Страница " + page + " из " + totalPages));
        }

        // [ПОИСК]
        List<String> searchLore = new ArrayList<>();
        searchLore.add(ChatColor.GRAY + "Нажми для поиска по нику");
        if (hasFilter) {
            searchLore.add(ChatColor.YELLOW + "Активный фильтр: " + ChatColor.WHITE + filter);
            searchLore.add(ChatColor.RED + "ПКМ для сброса фильтра");
        } else {
            searchLore.add(ChatColor.GRAY + "Фильтр не установлен");
        }
        inv.setItem(SLOT_SEARCH, makeItem(Material.BOOK,
                ChatColor.AQUA + "[ПОИСК]",
                searchLore.toArray(new String[0])));

        // Страница X из Y
        inv.setItem(SLOT_PAGE_INFO, makeItem(Material.PAPER,
                ChatColor.AQUA + "Страница " + (page + 1) + " из " + totalPages,
                ChatColor.GRAY + "Всего баунти: " + victims.size()));

        // Вперёд →
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeItem(Material.ARROW,
                    ChatColor.YELLOW + "Вперёд →",
                    ChatColor.GRAY + "Страница " + (page + 2) + " из " + totalPages));
        }

        player.openInventory(inv);
    }

    /**
     * Открывает Deposit GUI для установки баунти на жертву.
     * Вызывается из BountyCommand (/bounty create <player>).
     */
    public void openFor(Player sponsor, Player victim) {
        Inventory inv = Bukkit.createInventory(null, 9, DEPOSIT_TITLE);

        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            if (i != DEPOSIT_SLOT && i != CONFIRM_SLOT && i != CANCEL_SLOT)
                inv.setItem(i, glass);
        }

        inv.setItem(DEPOSIT_SLOT, makeItem(Material.LIME_STAINED_GLASS_PANE,
                ChatColor.GREEN + "» Положите предмет награды сюда «",
                ChatColor.GRAY + "Алмазы, незерит и любые другие предметы"));

        inv.setItem(CONFIRM_SLOT, makeItem(Material.EMERALD_BLOCK,
                ChatColor.GREEN + "" + ChatColor.BOLD + "✔  ПОДТВЕРДИТЬ",
                ChatColor.GRAY + "Установить баунти на " + ChatColor.RED + victim.getName(),
                ChatColor.GRAY + "Предмет будет изъят из инвентаря"));

        inv.setItem(CANCEL_SLOT, makeItem(Material.REDSTONE_BLOCK,
                ChatColor.RED + "" + ChatColor.BOLD + "✘  ОТМЕНА",
                ChatColor.GRAY + "Закрыть меню. Предмет возвращается."));

        depositSessions.put(sponsor.getUniqueId(), victim.getUniqueId());
        sponsor.openInventory(inv);
    }

    /**
     * Открывает Anvil GUI для поиска.
     * Вызывается внутри класса по клику на [ПОИСК].
     */
    private void openSearch(Player player) {
        searchSessions.add(player.getUniqueId());

        // openAnvil(location, force=true) — Paper API, доступен с 1.14+
        InventoryView view = player.openAnvil(player.getLocation(), true);
        if (view == null) {
            searchSessions.remove(player.getUniqueId());
            return;
        }

        // Кладём бумагу в слот 0 — «заготовка» для переименования (= ввод текста)
        ItemStack seed = makeItem(Material.PAPER,
                ChatColor.GRAY + "Введите ник игрока...",
                ChatColor.GRAY + "Напишите ник в поле выше и",
                ChatColor.GRAY + "нажмите на результат ▶");
        view.getTopInventory().setItem(0, seed);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── Browse GUI ────────────────────────────────────────────────────────
        if (isBrowseGUI(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            // Клики в нижнем инвентаре игрока внутри Browse GUI — игнорируем
            if (slot < 0 || slot >= 54) return;

            int     page   = pageMap.getOrDefault(player.getUniqueId(), 0);
            String  filter = filterMap.get(player.getUniqueId());

            switch (slot) {
                case SLOT_PREV -> openBrowse(player, page - 1, filter);

                case SLOT_NEXT -> openBrowse(player, page + 1, filter);

                case SLOT_SEARCH -> {
                    boolean isRightClick = event.isRightClick();
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (isRightClick && filter != null) {
                            // ПКМ — сбрасываем фильтр, возвращаем на 0 страницу
                            openBrowse(player, 0, null);
                        } else {
                            // ЛКМ — открываем поиск через Anvil GUI
                            openSearch(player);
                        }
                    }, 1L);
                }

                case SLOT_PAGE_INFO -> { /* информационный слот, действий нет */ }

                default -> { /* клик по голове — действий нет */ }
            }
            return;
        }

        // ── Deposit GUI ───────────────────────────────────────────────────────
        if (isDepositGUI(title)) {
            int slot = event.getRawSlot();

            // Любой слот кроме депозитного — блокируем
            if (slot != DEPOSIT_SLOT) event.setCancelled(true);

            // Блокируем shift-клик из инвентаря игрока в GUI (защита от дупа)
            if (slot >= 9 && event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }

            UUID victimUuid = depositSessions.get(player.getUniqueId());
            if (victimUuid == null) return;

            if (slot == CONFIRM_SLOT) {
                handleConfirm(player, victimUuid, event.getView().getTopInventory());
            } else if (slot == CANCEL_SLOT) {
                player.closeInventory(); // onClose вернёт предмет
            }
            return;
        }

        // ── Anvil (поиск) ─────────────────────────────────────────────────────
        if (searchSessions.contains(player.getUniqueId())
                && event.getInventory() instanceof AnvilInventory anvilInv) {

            // Слот 2 = выходной слот Anvil — игрок «подтверждает» ввод
            if (event.getRawSlot() == 2) {
                event.setCancelled(true);
                String query = anvilInv.getRenameText();
                searchSessions.remove(player.getUniqueId());
                player.closeInventory();
                final String q = (query != null && !query.isBlank()) ? query.trim() : null;
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> openBrowse(player, 0, q), 1L);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();

        if (isBrowseGUI(title)) {
            event.setCancelled(true);
            return;
        }

        if (isDepositGUI(title)) {
            // В Deposit GUI разрешаем перетаскивание только в слот депозита
            for (int slot : event.getRawSlots()) {
                if (slot < 9 && slot != DEPOSIT_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── Deposit GUI закрыт — возвращаем предмет из депозитного слота ─────
        if (isDepositGUI(title)) {
            depositSessions.remove(player.getUniqueId());
            ItemStack leftover = event.getInventory().getItem(DEPOSIT_SLOT);
            if (isRealItem(leftover)) {
                event.getInventory().setItem(DEPOSIT_SLOT, null);
                returnItem(player, leftover);
            }
            return;
        }

        // ── Browse GUI закрыт — pageMap/filterMap оставляем (resume при реоткрытии) ─

        // ── Anvil закрыт без подтверждения — всё равно применяем запрос ──────
        if (searchSessions.contains(player.getUniqueId())
                && event.getInventory() instanceof AnvilInventory anvilInv) {

            searchSessions.remove(player.getUniqueId());
            String query = anvilInv.getRenameText();

            // Не применяем дефолтный placeholder как фильтр
            final boolean isPlaceholder = query != null
                    && ChatColor.stripColor(query).equalsIgnoreCase("Введите ник игрока...");
            final String q = (!isPlaceholder && query != null && !query.isBlank())
                    ? query.trim() : null;

            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> openBrowse(player, 0, q), 1L);
        }
    }

    /**
     * PrepareAnvilEvent: делает поиск бесплатным (XP = 0) и всегда предоставляет
     * результат в выходном слоте, чтобы игрок мог нажать на него.
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!searchSessions.contains(player.getUniqueId())) return;

        // Стоимость = 0 (не тратим XP)
        event.getInventory().setRepairCost(0);

        // Строим result-предмет из слота 0 с текущим текстом в поле
        ItemStack base = event.getInventory().getItem(0);
        if (base == null) return;

        String renameText = event.getInventory().getRenameText();
        boolean hasQuery  = renameText != null && !renameText.isBlank();

        ItemStack result = base.clone();
        ItemMeta  meta   = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(hasQuery
                    ? ChatColor.AQUA + renameText
                    : ChatColor.GRAY + "Введите ник...");
            meta.setLore(hasQuery
                    ? List.of(ChatColor.GREEN + "Нажми, чтобы найти: " + ChatColor.WHITE + renameText)
                    : List.of(ChatColor.GRAY + "Введите ник в поле выше"));
            result.setItemMeta(meta);
        }
        event.setResult(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE: Deposit confirm
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleConfirm(Player sponsor, UUID victimUuid, Inventory gui) {
        ItemStack deposited = gui.getItem(DEPOSIT_SLOT);

        if (!isRealItem(deposited)) {
            sponsor.sendMessage(ChatColor.RED
                    + "[MoroBounty] Сначала положите предмет-награду в центральный слот!");
            return;
        }

        // Антидуп: проверяем, что предмет всё ещё есть в инвентаре
        if (!sponsor.getInventory().containsAtLeast(deposited, deposited.getAmount())) {
            sponsor.sendMessage(ChatColor.RED + "[MoroBounty] У вас больше нет этого предмета!");
            gui.setItem(DEPOSIT_SLOT, null);
            return;
        }

        if (manager.hasBounty(victimUuid)) {
            sponsor.sendMessage(ChatColor.RED
                    + "[MoroBounty] На этого игрока уже установлен активный баунти!");
            returnItem(sponsor, deposited);
            gui.setItem(DEPOSIT_SLOT, null);
            sponsor.closeInventory();
            return;
        }

        // ── Всё ок — фиксируем баунти ────────────────────────────────────────
        sponsor.getInventory().removeItem(deposited.clone());
        gui.setItem(DEPOSIT_SLOT, null);
        manager.setBounty(sponsor.getUniqueId(), victimUuid, deposited.clone());

        String victimName = Bukkit.getOfflinePlayer(victimUuid).getName();
        if (victimName == null) victimName = victimUuid.toString().substring(0, 8) + "...";

        sponsor.sendMessage(ChatColor.GREEN + "[MoroBounty] " + ChatColor.WHITE
                + "Баунти установлен на " + ChatColor.RED + victimName
                + ChatColor.WHITE + "! Награда: " + ChatColor.GOLD
                + deposited.getAmount() + "x " + deposited.getType().name());

        sponsor.closeInventory();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE: Item builders
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Строит голову жертвы с полной информацией о баунти.
     * Лор: "§cЦЕНА БОШКИ: §f<кол-во> §b<тип предмета>"
     */
    private ItemStack buildHeadItem(UUID victimUuid) {
        OfflinePlayer op   = Bukkit.getOfflinePlayer(victimUuid);
        String victimName  = op.getName() != null
                ? op.getName()
                : victimUuid.toString().substring(0, 8) + "...";

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        meta.setOwningPlayer(op);
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "☠ " + victimName);

        // Строим лор из десериализованного предмета
        List<String> lore = new ArrayList<>();
        ItemStack rewardItem = manager.getBountyItem(victimUuid);
        if (rewardItem != null) {
            lore.add(ChatColor.RED + "ЦЕНА БОШКИ: "
                    + ChatColor.WHITE + rewardItem.getAmount()
                    + " " + ChatColor.AQUA + prettyMaterial(rewardItem.getType().name()));

            // Если у предмета есть кастомное имя — показываем тоже
            if (rewardItem.hasItemMeta() && rewardItem.getItemMeta().hasDisplayName()) {
                lore.add(ChatColor.GRAY + "(" + rewardItem.getItemMeta().getDisplayName()
                        + ChatColor.GRAY + ")");
            }
        } else {
            // Fallback на текстовое описание из YAML (быстро, без десериализации)
            String desc = manager.getBountyDescription(victimUuid);
            lore.add(ChatColor.RED + "ЦЕНА БОШКИ: "
                    + ChatColor.AQUA + (desc != null ? desc : "???"));
        }
        lore.add(ChatColor.DARK_GRAY + "ID: " + victimUuid.toString().substring(0, 8) + "...");

        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    /** "DIAMOND_SWORD" → "Diamond Sword" (для красивого отображения в лоре) */
    private String prettyMaterial(String raw) {
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private ItemStack makeItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE: Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Сравнение без цветовых кодов — безопасно для любых вариаций § в заголовках. */
    private boolean isBrowseGUI(String title) {
        return ChatColor.stripColor(title)
                .equals(ChatColor.stripColor(BROWSE_TITLE));
    }

    private boolean isDepositGUI(String title) {
        return ChatColor.stripColor(title)
                .equals(ChatColor.stripColor(DEPOSIT_TITLE));
    }

    /** true = реальный предмет, а не стеклянный placeholder */
    private boolean isRealItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType() != Material.GRAY_STAINED_GLASS_PANE
            && item.getType() != Material.LIME_STAINED_GLASS_PANE;
    }

    /** Возвращает предмет в инвентарь; если места нет — дропает у ног. */
    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        overflow.values().forEach(l ->
                player.getWorld().dropItemNaturally(player.getLocation(), l));
    }
}
