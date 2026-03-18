package net.morosmp.combat;
import org.bukkit.Bukkit; import org.bukkit.entity.Player; import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType; import net.md_5.bungee.api.chat.TextComponent;
import java.util.HashMap; import java.util.Map; import java.util.UUID;
public class CombatManager {
    private final MoroCombat plugin;
    private final Map<UUID, Long> combatMap = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    public CombatManager(MoroCombat plugin) { this.plugin = plugin; startActionBarTask(); }
    public String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder(); boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true; sb.append(c); continue; }
            if (skip) { sb.append(c); skip = false; continue; }
            int idx = normal.indexOf(c);
            if (idx != -1) sb.append(small.charAt(idx)); else sb.append(c);
        } return sb.toString();
    }
    public void tagPlayer(Player victim, Player attacker) {
        long expire = System.currentTimeMillis() + (plugin.getConfig().getInt("combat-time", 15) * 1000L);
        combatMap.put(victim.getUniqueId(), expire); lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        combatMap.put(attacker.getUniqueId(), expire); lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());
    }
    public boolean isInCombat(Player player) {
        if (!combatMap.containsKey(player.getUniqueId())) return false;
        if (combatMap.get(player.getUniqueId()) > System.currentTimeMillis()) return true;
        combatMap.remove(player.getUniqueId()); return false;
    }
    public void removeCombat(Player player) { combatMap.remove(player.getUniqueId()); }
    public UUID getLastAttacker(Player player) { return lastAttacker.get(player.getUniqueId()); }
    private void startActionBarTask() {
        new BukkitRunnable() {
            @Override public void run() {
                String rawFormat = plugin.getConfig().getString("messages.action-bar", "§c§lIn Combat: §e%time%s");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isInCombat(player)) {
                        long remaining = (combatMap.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(sc(rawFormat.replace("%time%", String.valueOf(remaining + 1)))));
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 10L);
    }
}