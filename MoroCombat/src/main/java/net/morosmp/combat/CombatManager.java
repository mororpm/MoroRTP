package net.morosmp.combat;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager {

    private final MoroCombat plugin;

    // Maps a player's UUID → the timestamp (ms) when their combat tag expires
    private final Map<UUID, Long> combatMap    = new HashMap<>();
    // Maps a player's UUID → the UUID of the last player who hit them
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();

    public CombatManager(MoroCombat plugin) {
        this.plugin = plugin;
        startActionBarTask();
    }

    // -------------------------------------------------------------------------
    // Small-caps utility (kept in-class so CombatListener can call manager.sc())
    // -------------------------------------------------------------------------
    public String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small  = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true; sb.append(c); continue; }
            if (skip)     { sb.append(c); skip = false; continue; }
            int idx = normal.indexOf(c);
            sb.append(idx != -1 ? small.charAt(idx) : c);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // tagPlayer — BOTH the victim and the attacker receive a combat tag.
    //
    // Each side records the OTHER player as their lastAttacker so that if either
    // combat-logs the kill can still be correctly attributed.
    // -------------------------------------------------------------------------
    public void tagPlayer(Player victim, Player attacker) {
        long expire = System.currentTimeMillis()
                + (plugin.getConfig().getInt("combat-time", 15) * 1000L);

        // Tag victim → points at attacker
        combatMap.put(victim.getUniqueId(),   expire);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());

        // Tag attacker → points at victim
        combatMap.put(attacker.getUniqueId(),   expire);
        lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // isInCombat — returns true only if the tag hasn't expired yet.
    // Automatically cleans up stale entries.
    // -------------------------------------------------------------------------
    public boolean isInCombat(Player player) {
        UUID uuid = player.getUniqueId();
        if (!combatMap.containsKey(uuid)) return false;

        if (combatMap.get(uuid) > System.currentTimeMillis()) return true;

        // Tag expired — clean up both maps
        combatMap.remove(uuid);
        lastAttacker.remove(uuid);
        return false;
    }

    // -------------------------------------------------------------------------
    // removeCombat — fully wipes a player's combat state from BOTH maps.
    //
    // FIX: The old implementation only removed from combatMap. The lastAttacker
    // entry was left dangling. Now both maps are cleared atomically, ensuring
    // the player's UUID does not linger in memory after they combat-log.
    // -------------------------------------------------------------------------
    public void removeCombat(Player player) {
        UUID uuid = player.getUniqueId();
        combatMap.remove(uuid);
        lastAttacker.remove(uuid);   // ← this line was missing in the original
    }

    // -------------------------------------------------------------------------
    // getLastAttacker — returns the UUID of whoever last hit this player, or
    // null if no record exists (e.g. after removeCombat() was called).
    // -------------------------------------------------------------------------
    public UUID getLastAttacker(Player player) {
        return lastAttacker.get(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Action-bar ticker — shows the remaining combat seconds above the hotbar
    // -------------------------------------------------------------------------
    private void startActionBarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                String rawFormat = plugin.getConfig().getString(
                        "messages.action-bar", "§cIn Combat: §e%time%s");

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isInCombat(player)) {
                        long remaining = (combatMap.get(player.getUniqueId())
                                - System.currentTimeMillis()) / 1000;
                        String bar = sc(rawFormat.replace("%time%",
                                String.valueOf(remaining + 1)));
                        player.spigot().sendMessage(
                                ChatMessageType.ACTION_BAR, new TextComponent(bar));
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 10L);
    }
}
