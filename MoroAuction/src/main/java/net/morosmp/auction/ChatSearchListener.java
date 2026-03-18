package net.morosmp.auction;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChatSearchListener implements Listener {
    private final MoroAuction plugin;
    public final Set<UUID> searchMode = new HashSet<>();

    public ChatSearchListener(MoroAuction plugin) { this.plugin = plugin; }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (searchMode.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            searchMode.remove(e.getPlayer().getUniqueId());
            
            String query = e.getMessage().trim();
            if (query.equalsIgnoreCase("cancel")) {
                e.getPlayer().sendMessage(Utils.color("&cSearch cancelled."));
                return;
            }
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                AuctionState state = plugin.getAuctionGUI().getState(e.getPlayer());
                state.search = query;
                state.page = 0; // Сбрасываем на первую страницу при новом поиске
                plugin.getAuctionGUI().openGUI(e.getPlayer());
            });
        }
    }
}