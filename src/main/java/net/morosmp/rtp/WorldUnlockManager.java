package net.morosmp.rtp;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class WorldUnlockManager implements Listener {
    private final MoroRTP plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Set<String> unlockedWorlds = new HashSet<>();

    public WorldUnlockManager(MoroRTP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        loadData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        List<String> savedWorlds = dataConfig.getStringList("unlocked-worlds");
        unlockedWorlds.addAll(savedWorlds);

        // Overworld is always unlocked
        if (unlockedWorlds.add("world")) {
            saveData();
        }
    }

    private void saveData() {
        dataConfig.set("unlocked-worlds", new ArrayList<>(unlockedWorlds));
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml!");
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        String worldName = event.getPlayer().getWorld().getName();
        if (unlockedWorlds.add(worldName)) {
            saveData();
            plugin.getLogger().info("New dimension unlocked for RTP: " + worldName);
            // Optional: Broadcast to server about the new dimension
        }
    }

    public boolean isWorldUnlocked(String worldName) {
        return unlockedWorlds.contains(worldName);
    }

    public World getRandomUnlockedWorld() {
        if (unlockedWorlds.isEmpty())
            return Bukkit.getWorld("world");

        List<String> list = new ArrayList<>(unlockedWorlds);
        String randomName = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        World world = Bukkit.getWorld(randomName);

        return world != null ? world : Bukkit.getWorld("world");
    }
}