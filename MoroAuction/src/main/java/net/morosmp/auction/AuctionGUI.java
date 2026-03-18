package net.morosmp.auction;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
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

import java.util.*;

public class AuctionGUI implements Listener {
    private final MoroAuction plugin;
    public final Map<UUID, AuctionState> states = new HashMap<>();

    public AuctionGUI(MoroAuction plugin) { this.plugin = plugin; }

    public AuctionState getState(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new AuctionState());
    }

    public void openGUI(Player player) {
        AuctionState state = getState(player);
        Inventory inv = Bukkit.createInventory(null, 54, Utils.color("&8Auction House | Page " + (state.page + 1)));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            
            // Фильтр: Ищем только активные лоты (время истечения > сейчас)
            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.gt("expire_at", now));
            
            if (!state.search.isEmpty()) {
                filters.add(Filters.regex("material", "(?i).*" + state.search + ".*"));
            }

            Bson sortQuery = Sorts.descending("listed_at");
            if (state.sort.equals("highest")) sortQuery = Sorts.descending("price");
            else if (state.sort.equals("lowest")) sortQuery = Sorts.ascending("price");

            FindIterable<Document> docs = plugin.getAuctionManager().getCollection()
                    .find(Filters.and(filters))
                    .sort(sortQuery)
                    .skip(state.page * 45)
                    .limit(46);

            List<Document> items = new ArrayList<>();
            for (Document doc : docs) items.add(doc);

            Bukkit.getScheduler().runTask(plugin, () -> {
                int slot = 0;
                boolean hasNext = false;

                for (Document doc : items) {
                    if (slot >= 45) { hasNext = true; break; }
                    ItemStack item = plugin.getAuctionManager().deserializeItem(doc.getString("item_base64"));
                    if (item == null) continue;

                    ItemMeta meta = item.getItemMeta();
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    lore.add(Utils.color("&8----------------------"));
                    lore.add(Utils.color("&7Seller: &e" + doc.getString("seller_name")));
                    lore.add(Utils.color("&7Price: <#F9A826>&l$" + doc.getDouble("price")));
                    lore.add("");
                    lore.add(Utils.color("&eClick to purchase!"));
                    lore.add(ChatColor.BLACK + "ah:" + doc.getObjectId("_id").toHexString());
                    meta.setLore(lore); item.setItemMeta(meta);
                    inv.setItem(slot++, item);
                }

                // Панель управления
                if (state.page > 0) inv.setItem(45, createBtn(Material.ARROW, "&ePrevious Page", ""));
                
                String sortName = state.sort.equals("newest") ? "Newest First" : (state.sort.equals("highest") ? "Highest Price" : "Lowest Price");
                inv.setItem(46, createBtn(Material.HOPPER, "&bSort: &f" + sortName, "&7Click to change sort mode"));
                
                String searchName = state.search.isEmpty() ? "None" : state.search;
                inv.setItem(48, createBtn(Material.OAK_SIGN, "&aSearch", "&7Current: &f" + searchName, "&7Click to search for an item"));
                
                inv.setItem(49, createBtn(Material.GOLD_BLOCK, "&6Your Balance", "&e$" + String.format("%.0f", plugin.getEconomy().getBalance(player))));
                
                inv.setItem(50, createBtn(Material.ENDER_CHEST, "&dMy Auctions", "&7Click to manage your listings", "&7and claim expired items"));
                
                if (hasNext) inv.setItem(53, createBtn(Material.ARROW, "&eNext Page", ""));

                player.openInventory(inv);
            });
        });
    }

    private ItemStack createBtn(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Utils.color(name));
        List<String> lore = new ArrayList<>();
        for (String l : loreLines) lore.add(Utils.color(l));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains(Utils.sc("Auction House"))) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        
        Player p = (Player) event.getWhoClicked();
        String itemName = event.getCurrentItem().getItemMeta().getDisplayName();
        AuctionState state = getState(p);

        if (itemName.contains(Utils.sc("Previous"))) { state.page--; openGUI(p); }
        else if (itemName.contains(Utils.sc("Next"))) { state.page++; openGUI(p); }
        else if (itemName.contains(Utils.sc("Sort"))) {
            if (state.sort.equals("newest")) state.sort = "highest";
            else if (state.sort.equals("highest")) state.sort = "lowest";
            else state.sort = "newest";
            state.page = 0; openGUI(p);
        }
        else if (itemName.contains(Utils.sc("Search"))) {
            p.closeInventory();
            plugin.getChatListener().searchMode.add(p.getUniqueId());
            p.sendMessage(Utils.color(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.type-search")));
        }
        else if (itemName.contains(Utils.sc("My Auctions"))) {
            plugin.getPlayerGUI().openPlayerGUI(p);
        }
        else if (event.getCurrentItem().hasItemMeta() && event.getCurrentItem().getItemMeta().hasLore()) {
            List<String> lore = event.getCurrentItem().getItemMeta().getLore();
            String hiddenTag = lore.get(lore.size() - 1);
            if (hiddenTag.startsWith(ChatColor.BLACK + "ah:")) {
                String hexId = hiddenTag.split(":")[1];
                processPurchase(p, new ObjectId(hexId));
            }
        }
    }

    private void processPurchase(Player p, ObjectId id) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Document doc = plugin.getAuctionManager().getCollection().find(Filters.eq("_id", id)).first();
            if (doc == null) { p.sendMessage(Utils.color("&cItem already sold or removed!")); return; }

            double price = doc.getDouble("price");
            if (doc.getString("seller_uuid").equals(p.getUniqueId().toString())) {
                p.sendMessage(Utils.color("&cYou cannot buy your own item! Manage it in 'My Auctions'.")); return;
            }

            if (plugin.getEconomy().getBalance(p) >= price) {
                Document deleted = plugin.getAuctionManager().getCollection().findOneAndDelete(Filters.eq("_id", id));
                if (deleted != null) {
                    plugin.getEconomy().withdrawPlayer(p, price);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(UUID.fromString(doc.getString("seller_uuid"))), price);
                        ItemStack item = plugin.getAuctionManager().deserializeItem(doc.getString("item_base64"));
                        p.getInventory().addItem(item);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        p.sendMessage(Utils.color(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.purchased")
                            .replace("%item%", item.getType().name()).replace("%price%", String.valueOf(price))));
                        p.closeInventory();
                    });
                } else p.sendMessage(Utils.color("&cItem was just bought by someone else!"));
            } else p.sendMessage(Utils.color("&cNot enough money!"));
        });
    }
}