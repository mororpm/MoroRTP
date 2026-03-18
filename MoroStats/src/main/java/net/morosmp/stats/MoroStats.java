package net.morosmp.stats;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

public class MoroStats extends JavaPlugin implements Listener {
    private MongoClient mongoClient;
    private MongoCollection<Document> collection;
    private static Economy econ = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();

        String uri = getConfig().getString("mongodb-uri", "mongodb://localhost:27017");
        String dbName = getConfig().getString("database-name", "morosmp");

        try {
            mongoClient = MongoClients.create(uri);
            collection = mongoClient.getDatabase(dbName).getCollection("players");
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("✅ MoroStats connected to MongoDB!");
        } catch (Exception e) {
            getLogger().severe("❌ Failed to connect to MongoDB: " + e.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            Document stats = new Document("username", p.getName().toLowerCase())
                .append("kills", p.getStatistic(Statistic.PLAYER_KILLS))
                .append("deaths", p.getStatistic(Statistic.DEATHS))
                .append("balance", econ != null ? econ.getBalance(p) : 0)
                .append("playtime", (p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 3600) + "h")
                .append("isPrivate", false);

            if (collection != null) {
                collection.replaceOne(new Document("username", p.getName().toLowerCase()), stats, new ReplaceOptions().upsert(true));
            }
        });
    }

    @Override
    public void onDisable() {
        if (mongoClient != null) mongoClient.close();
    }
}