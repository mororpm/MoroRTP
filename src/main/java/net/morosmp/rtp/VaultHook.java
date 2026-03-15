package net.morosmp.rtp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {
    private final MoroRTP plugin;
    private Economy econ = null;

    public VaultHook(MoroRTP plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public boolean hasEconomy() {
        return econ != null;
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        if (!hasEconomy()) return true;
        if (amount <= 0) return true;
        return econ.has((OfflinePlayer) player, amount);
    }

    public void withdrawMoney(Player player, double amount) {
        if (!hasEconomy() || amount <= 0) return;
        econ.withdrawPlayer((OfflinePlayer) player, amount);
    }
}
