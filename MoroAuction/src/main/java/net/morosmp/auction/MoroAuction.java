package net.morosmp.auction;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MoroAuction extends JavaPlugin {
    private MongoClient mongoClient;
    private Economy econ = null;
    private AuctionManager auctionManager;
    private AuctionGUI auctionGUI;
    private PlayerAuctionGUI playerGUI;
    private ChatSearchListener chatListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault not found!");
            getServer().getPluginManager().disablePlugin(this); return;
        }
        try {
            mongoClient = MongoClients.create("mongodb://localhost:27017");
            MongoDatabase database = mongoClient.getDatabase("morosmp");
            auctionManager = new AuctionManager(this, database);
        } catch (Exception e) {
            getLogger().severe("MongoDB connection failed!"); return;
        }

        auctionGUI = new AuctionGUI(this);
        playerGUI = new PlayerAuctionGUI(this);
        chatListener = new ChatSearchListener(this);

        getCommand("ah").setExecutor(new AuctionCommand(this));
        getServer().getPluginManager().registerEvents(auctionGUI, this);
        getServer().getPluginManager().registerEvents(playerGUI, this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getLogger().info("MoroAuction Premium Loaded! Chat Search & Player GUIs active.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider(); return econ != null;
    }

    public Economy getEconomy() { return econ; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public AuctionGUI getAuctionGUI() { return auctionGUI; }
    public PlayerAuctionGUI getPlayerGUI() { return playerGUI; }
    public ChatSearchListener getChatListener() { return chatListener; }

    @Override
    public void onDisable() { if (mongoClient != null) mongoClient.close(); }
}