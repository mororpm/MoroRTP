package net.morosmp.shop;

import org.bukkit.plugin.java.JavaPlugin;

public class MoroShop extends JavaPlugin {
    private VaultHook vaultHook;
    private ShopGUI shopGUI;
    private BuyGUI buyGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        vaultHook = new VaultHook();
        if (!vaultHook.setupEconomy()) {
            getLogger().severe("Vault not found! Disabling MoroShop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        shopGUI = new ShopGUI(this);
        buyGUI = new BuyGUI(this);
        getCommand("shop").setExecutor(new ShopCommand(this));
        
        getServer().getPluginManager().registerEvents(shopGUI, this);
        getServer().getPluginManager().registerEvents(buyGUI, this);
        
        getLogger().info("MoroShop Premium Enabled! Smart BuyGUI active.");
    }

    public VaultHook getVaultHook() { return vaultHook; }
    public ShopGUI getShopGUI() { return shopGUI; }
    public BuyGUI getBuyGUI() { return buyGUI; }
}