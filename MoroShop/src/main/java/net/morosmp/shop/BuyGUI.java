package net.morosmp.shop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BuyGUI implements Listener {
    private final MoroShop plugin;

    public BuyGUI(MoroShop plugin) { this.plugin = plugin; }

    public void openBuyMenu(Player player, String categoryKey, String itemKey, int amount) {
        if (amount < 1) amount = 1;
        if (amount > 2304) amount = 2304; // Лимит: фулл инвентарь (36 слотов * 64)

        String title = Utils.color("&8Confirm Purchase", true);
        Inventory inv = Bukkit.createInventory(null, 27, title);

        double pricePerItem = plugin.getConfig().getDouble("shops." + categoryKey + ".items." + itemKey + ".price");
        double totalPrice = pricePerItem * amount;
        Material mat = Material.matchMaterial(plugin.getConfig().getString("shops." + categoryKey + ".items." + itemKey + ".material", "DIRT"));

        // Центральный предмет с информацией
        ItemStack center = new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
        ItemMeta centerMeta = center.getItemMeta();
        centerMeta.setDisplayName(Utils.color("&e&l" + mat.name().replace("_", " "), true));
        List<String> lore = new ArrayList<>();
        lore.add(Utils.color("&7Quantity: &f" + amount, true));
        lore.add(Utils.color("&7Total Price: &a$" + String.format("%.2f", totalPrice), true));
        lore.add("");
        // Сохраняем стейт прямо в предмет
        lore.add(ChatColor.BLACK + "state:" + categoryKey + ":" + itemKey + ":" + amount);
        centerMeta.setLore(lore);
        center.setItemMeta(centerMeta);
        inv.setItem(13, center);

        // Кнопки управления
        inv.setItem(10, createBtn(Material.RED_STAINED_GLASS_PANE, "&c-10"));
        inv.setItem(11, createBtn(Material.RED_STAINED_GLASS_PANE, "&c-1"));
        inv.setItem(15, createBtn(Material.LIME_STAINED_GLASS_PANE, "&a+1"));
        inv.setItem(16, createBtn(Material.LIME_STAINED_GLASS_PANE, "&a+10"));

        inv.setItem(21, createBtn(Material.BARRIER, "&c&lCancel"));
        inv.setItem(23, createBtn(Material.EMERALD, "&a&lConfirm Purchase"));

        player.openInventory(inv);
    }

    private ItemStack createBtn(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Utils.color(name, true));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(Utils.color("&8Confirm Purchase", true))) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player p = (Player) event.getWhoClicked();
        Inventory inv = event.getClickedInventory();
        if (inv == null || event.getRawSlot() >= 27) return; // Игнорируем клики по нижнему инвентарю

        ItemStack center = inv.getItem(13);
        if (center == null || !center.hasItemMeta() || !center.getItemMeta().hasLore()) return;
        
        List<String> lore = center.getItemMeta().getLore();
        String state = lore.get(lore.size() - 1).replace(ChatColor.BLACK.toString(), "");
        String[] parts = state.split(":");
        if (parts.length != 4) return;

        String cat = parts[1];
        String itemKey = parts[2];
        int currentAmt = Integer.parseInt(parts[3]);

        int slot = event.getRawSlot();
        playSound(p, "click");

        // Логика калькулятора
        if (slot == 10) openBuyMenu(p, cat, itemKey, currentAmt - 10);
        else if (slot == 11) openBuyMenu(p, cat, itemKey, currentAmt - 1);
        else if (slot == 15) openBuyMenu(p, cat, itemKey, currentAmt + 1);
        else if (slot == 16) openBuyMenu(p, cat, itemKey, currentAmt + 10);
        else if (slot == 21) plugin.getShopGUI().openCategory(p, cat); // Отмена
        else if (slot == 23) { // Подтверждение
            processPurchase(p, cat, itemKey, currentAmt);
        }
    }

    private void processPurchase(Player p, String cat, String itemKey, int amount) {
        double price = plugin.getConfig().getDouble("shops." + cat + ".items." + itemKey + ".price") * amount;
        Material mat = Material.matchMaterial(plugin.getConfig().getString("shops." + cat + ".items." + itemKey + ".material"));

        // Защита: проверяем пустые слоты (1 слот вмещает максимум стак конкретного предмета)
        int requiredSlots = (int) Math.ceil((double) amount / mat.getMaxStackSize());
        if (getEmptySlots(p) < requiredSlots) {
            p.sendMessage(Utils.color(plugin.getConfig().getString("messages.inventory-full").replace("%prefix%", plugin.getConfig().getString("prefix")), true));
            playSound(p, "error");
            return;
        }

        if (plugin.getVaultHook().hasEnough(p, price)) {
            plugin.getVaultHook().withdraw(p, price);
            
            // Выдаем предметы пачками с учетом MaxStackSize (Жемчуг эндера стакается по 16, а не по 64!)
            int remaining = amount;
            while (remaining > 0) {
                int give = Math.min(remaining, mat.getMaxStackSize());
                p.getInventory().addItem(new ItemStack(mat, give));
                remaining -= give;
            }
            
            playSound(p, "success");
            p.sendMessage(Utils.color(plugin.getConfig().getString("messages.purchased")
                    .replace("%prefix%", plugin.getConfig().getString("prefix"))
                    .replace("%qty%", String.valueOf(amount))
                    .replace("%item%", mat.name().replace("_", " "))
                    .replace("%price%", String.format("%.2f", price)), true));
            p.closeInventory();
        } else {
            playSound(p, "error");
            p.sendMessage(Utils.color(plugin.getConfig().getString("messages.not-enough-money").replace("%prefix%", plugin.getConfig().getString("prefix")), true));
        }
    }

    private void playSound(Player p, String type) {
        try {
            Sound s = Sound.valueOf(plugin.getConfig().getString("sounds." + type));
            p.playSound(p.getLocation(), s, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    private int getEmptySlots(Player p) {
        int count = 0;
        for (int i = 0; i < 36; i++) { // Проверяем только основной инвентарь + хотбар
            if (p.getInventory().getItem(i) == null) count++;
        }
        return count;
    }
}