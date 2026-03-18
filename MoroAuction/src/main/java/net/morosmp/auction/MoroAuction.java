package net.morosmp.auction;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.plugin.java.JavaPlugin;

public class MoroAuction extends JavaPlugin {

    private MongoClient mongoClient;
    private AuctionManager auctionManager;
    private AuctionGUI auctionGUI;
    private PlayerAuctionGUI playerGUI;
    private ChatSearchListener chatListener;
    private Economy econ;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String uri = getConfig().getString("database.uri");
        String dbName = getConfig().getString("database.name");

        mongoClient = MongoClients.create(uri);
        MongoDatabase db = mongoClient.getDatabase(dbName);

        auctionManager = new AuctionManager(this, db);

        // ✅ СОЗДАЁМ ОДИН РАЗ
        auctionGUI = new AuctionGUI(this);
        playerGUI = new PlayerAuctionGUI(this);
        chatListener = new ChatSearchListener(this);

        if (getCommand("ah") != null) {
            getCommand("ah").setExecutor(new AuctionCommand(this));
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(auctionGUI, this);
        pm.registerEvents(playerGUI, this);
        pm.registerEvents(chatListener, this);
    }

    @Override
    public void onDisable() {
        if (mongoClient != null) mongoClient.close();
    }

    private boolean setupEconomy() {
        var rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    // 🔥 ВОТ ЧЕГО ТЕБЕ НЕ ХВАТАЛО
    public AuctionGUI getAuctionGUI() {
        return auctionGUI;
    }

    public PlayerAuctionGUI getPlayerGUI() {
        return playerGUI;
    }

    public ChatSearchListener getChatListener() {
        return chatListener;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public Economy getEconomy() {
        return econ;
    }
}