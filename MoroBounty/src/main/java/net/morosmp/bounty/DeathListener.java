package net.morosmp.bounty;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {
    private final MoroBounty plugin;
    public DeathListener(MoroBounty plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity(); Player killer = victim.getKiller();
        double baseBounty = plugin.getBountyManager().getTotalBounty(victim.getUniqueId());
        if (baseBounty <= 0) return;

        if (killer == null) {
            plugin.getBountyManager().clearBounties(victim.getUniqueId());
            String msg = plugin.getConfig().getString("messages.bounty-burned")
                .replace("%amount%", String.format("%.0f", baseBounty))
                .replace("%victim%", victim.getName());
            Bukkit.broadcastMessage(Utils.color(msg, true));
            return;
        }

        if (plugin.getBountyManager().isSameIP(killer, victim) || killer.equals(victim)) {
            plugin.getBountyManager().clearBounties(victim.getUniqueId());
            double penalty = plugin.getVaultHook().getBalance(victim) * 0.20; 
            plugin.getVaultHook().withdraw(victim, penalty);
            String msg = plugin.getConfig().getString("messages.fraud-detected")
                .replace("%penalty%", String.format("%.0f", penalty));
            killer.sendMessage(Utils.color(msg, true));
            return;
        }

        double walletReward = baseBounty * Math.pow(0.8, plugin.getBountyManager().getKillCount(killer.getUniqueId(), victim.getUniqueId())) * 0.8;
        plugin.getVaultHook().deposit(killer, walletReward); 
        plugin.getBountyManager().clearBounties(victim.getUniqueId()); 
        plugin.getBountyManager().addKillRecord(killer.getUniqueId(), victim.getUniqueId());

        victim.getWorld().strikeLightningEffect(victim.getLocation());
        
        String broadcast = plugin.getConfig().getString("messages.bounty-claimed")
            .replace("%killer%", killer.getName())
            .replace("%victim%", victim.getName())
            .replace("%amount%", String.format("%.0f", walletReward));
            
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
            p.sendMessage(Utils.color(broadcast, true));
            p.sendTitle(Utils.color("&4&lBOUNTY CLAIMED", true), Utils.color("&e" + killer.getName() + " &7claimed &a$" + String.format("%.0f", walletReward), true), 10, 60, 20);
        }
    }
}