package net.morosmp.auction;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import org.bson.Document;
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

import java.util.ArrayList;
import java.util.List;

public class PlayerAuctionGUI implements Listener {
    private final MoroAuction plugin;

    public PlayerAuctionGUI(MoroAuction plugin) { this.plugin = plugin; }

    public void openPlayerGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Utils.color("&8My Auctions"));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FindIterable<Document> docs = plugin.getAuctionManager().getCollection()
                    .find(Filters.eq("seller_uuid", player.getUniqueId().toString()))
                    .limit(45);

            List<Document> items = new ArrayList<>();
            for (Document doc : docs) items.add(doc);

            Bukkit.getScheduler().runTask(plugin, () -> {
                int slot = 0;
                long now = System.currentTimeMillis();

                for (Document doc : items) {
                    ItemStack item = plugin.getAuctionManager().deserializeItem(doc.getString("item_base64"));
                    if (item == null) continue;

                    boolean expired = now > doc.getLong("expire_at");

                    ItemMeta meta = item.getItemMeta();
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    lore.add(Utils.color("&8----------------------"));
                    lore.add(Utils.color("&7Price: <#F9A826>&l$" + doc.getDouble("price")));
                    
                    if (expired) {
                        lore.add(Utils.color("&c&lEXPIRED"));
                        lore.add(Utils.color("&eClick to reclaim item!"));
                    } else {
                        lore.add(Utils.color("&a&lACTIVE"));
                        lore.add(Utils.color("&cClick to cancel listing!"));
                    }
                    
                    lore.add(ChatColor.BLACK + "my:" + doc.getObjectId("_id").toHexString());
                    meta.setLore(lore); item.setItemMeta(meta);
                    inv.setItem(slot++, item);
                }

                ItemStack back = new ItemStack(Material.ARROW);
                ItemMeta backMeta = back.getItemMeta();
                backMeta.setDisplayName(Utils.color("&cBack to Auction House"));
                back.setItemMeta(backMeta);
                inv.setItem(45, back);

                player.openInventory(inv);
            });
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains(Utils.sc("My Auctions"))) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player p = (Player) event.getWhoClicked();
        
        if (event.getCurrentItem().getType() == Material.ARROW) {
            plugin.getAuctionGUI().openGUI(p);
            return;
        }

        if (event.getCurrentItem().hasItemMeta() && event.getCurrentItem().getItemMeta().hasLore()) {
            List<String> lore = event.getCurrentItem().getItemMeta().getLore();
            String hiddenTag = lore.get(lore.size() - 1);
            if (hiddenTag.startsWith(ChatColor.BLACK + "my:")) {
                String hexId = hiddenTag.split(":")[1];
                reclaimItem(p, new ObjectId(hexId));
            }
        }
    }

    private void reclaimItem(Player p, ObjectId id) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Document deleted = plugin.getAuctionManager().getCollection().findOneAndDelete(Filters.eq("_id", id));
            if (deleted != null) {
                ItemStack item = plugin.getAuctionManager().deserializeItem(deleted.getString("item_base64"));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.getInventory().addItem(item);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    p.sendMessage(Utils.color(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.cancelled")));
                    openPlayerGUI(p); // Refresh GUI
                });
            }
        });
    }
}