package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BountyGUI implements Listener {
    private final MoroBounty plugin;
    public BountyGUI(MoroBounty plugin) { this.plugin = plugin; }

    public void openGUI(Player player, int page) {
        String title = Utils.color(plugin.getConfig().getString("gui.title") + " &8| Page " + (page + 1), true);
        Inventory inv = Bukkit.createInventory(null, 54, title);
        
        try (PreparedStatement ps = plugin.getBountyManager().getDb().getConnection().prepareStatement(
                "SELECT target_uuid, SUM(amount) as total FROM active_bounties GROUP BY target_uuid ORDER BY total DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, 46); ps.setInt(2, page * 45); 
            ResultSet rs = ps.executeQuery();
            
            int slot = 0; boolean hasNextPage = false;
            while (rs.next()) {
                if (slot >= 45) { hasNextPage = true; break; }
                UUID targetUuid = UUID.fromString(rs.getString("target_uuid"));
                inv.setItem(slot++, createHead(targetUuid, rs.getDouble("total")));
            }
            
            if (page > 0) inv.setItem(45, createSimpleButton(Material.ARROW, "&ePrevious Page"));
            inv.setItem(48, getConfigItem("gui.sort", Material.HOPPER));
            inv.setItem(49, getConfigItem("gui.info", Material.BOOK));
            inv.setItem(50, getConfigItem("gui.search", Material.OAK_SIGN));
            if (hasNextPage) inv.setItem(53, createSimpleButton(Material.ARROW, "&eNext Page"));
            
        } catch (SQLException e) { e.printStackTrace(); }
        player.openInventory(inv);
    }

    private ItemStack getConfigItem(String path, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Utils.color(plugin.getConfig().getString(path + ".displayname"), true));
        List<String> rawLore = plugin.getConfig().getStringList(path + ".lore");
        List<String> coloredLore = new ArrayList<>();
        for(String line : rawLore) coloredLore.add(Utils.color(line, true));
        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHead(UUID targetUuid, double amount) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));
        meta.setDisplayName(Utils.color("&c&l" + Bukkit.getOfflinePlayer(targetUuid).getName(), true));
        List<String> lore = new ArrayList<>();
        lore.add(Utils.color("&7Reward: <#00f986>&l$" + String.format("%.0f", amount), true));
        meta.setLore(lore); head.setItemMeta(meta); return head;
    }

    private ItemStack createSimpleButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta(); 
        meta.setDisplayName(Utils.color(name, true)); item.setItemMeta(meta); return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains(Utils.sc("Wanted"))) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        
        Player player = (Player) event.getWhoClicked();
        String itemName = event.getCurrentItem().getItemMeta().getDisplayName();
        int currentPage = Integer.parseInt(event.getView().getTitle().replaceAll("[^0-9]", "")) - 1;
        
        if (itemName.contains(Utils.sc("Previous"))) openGUI(player, currentPage - 1);
        else if (itemName.contains(Utils.sc("Next"))) openGUI(player, currentPage + 1);
        else if (itemName.contains(Utils.sc("Search"))) {
            player.closeInventory();
            player.sendMessage(Utils.color("&eType player name in chat...", true));
        }
    }
}