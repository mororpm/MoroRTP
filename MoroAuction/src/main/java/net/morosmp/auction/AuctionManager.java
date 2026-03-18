package net.morosmp.auction;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class AuctionManager {
    private final MoroAuction plugin;
    private final MongoCollection<Document> collection;

    public AuctionManager(MoroAuction plugin, MongoDatabase db) {
        this.plugin = plugin;
        this.collection = db.getCollection("auctions");
    }

    public MongoCollection<Document> getCollection() { return collection; }

    public String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream io = new ByteArrayOutputStream();
            BukkitObjectOutputStream os = new BukkitObjectOutputStream(io);
            os.writeObject(item); os.flush();
            return Base64.getEncoder().encodeToString(io.toByteArray());
        } catch (Exception e) { return null; }
    }

    public ItemStack deserializeItem(String base64) {
        try {
            byte[] serializedObject = Base64.getDecoder().decode(base64);
            ByteArrayInputStream in = new ByteArrayInputStream(serializedObject);
            BukkitObjectInputStream is = new BukkitObjectInputStream(in);
            return (ItemStack) is.readObject();
        } catch (Exception e) { return null; }
    }

    public void listObject(Player p, ItemStack item, double price) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        String b64 = serializeItem(item);
        if (b64 == null) return;

        long expireTime = System.currentTimeMillis() +
                plugin.getConfig().getInt("settings.expire-hours") * 3600000L;

        Document doc = new Document("seller_uuid", p.getUniqueId().toString())
                .append("seller_name", p.getName())
                .append("price", price)
                .append("item_base64", b64)
                .append("listed_at", System.currentTimeMillis())
                .append("expire_at", expireTime);

        collection.insertOne(doc);
    });
    }
}