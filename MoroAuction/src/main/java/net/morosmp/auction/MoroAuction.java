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
            getLogger().severe("Vault missing!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ✅ CONFIG-BASED Mongo
        String uri = getConfig().getString("database.uri");
        String dbName = getConfig().getString("database.name");

        mongoClient = MongoClients.create(uri);
        MongoDatabase db = mongoClient.getDatabase(dbName);

        auctionManager = new AuctionManager(this, db);

        // ✅ SINGLETON GUI INSTANCES
        auctionGUI = new AuctionGUI(this);
        playerGUI = new PlayerAuctionGUI(this);
        chatListener = new ChatSearchListener(this);

        // ✅ SAFE COMMAND REGISTRATION
        if (getCommand("ah") != null) {
            getCommand("ah").setExecutor(new AuctionCommand(this));
        }

        // ✅ EVENT REGISTRATION
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

    public Economy getEconomy() { return econ; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public AuctionGUI getAuctionGUI() { return auctionGUI; }
    public PlayerAuctionGUI getPlayerGUI() { return playerGUI; }
    public ChatSearchListener getChatListener() { return chatListener; }
}