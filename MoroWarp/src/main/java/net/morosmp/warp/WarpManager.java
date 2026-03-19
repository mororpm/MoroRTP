package net.morosmp.warp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class WarpManager {

    private final WarpPlugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public WarpManager(WarpPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        dataFile   = new File(plugin.getDataFolder(), "warps.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try { dataConfig.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save warps.yml", e);
        }
    }

    public boolean warpExists(String name) {
        return dataConfig.contains("warps." + name.toLowerCase());
    }

    public void setWarp(String name, Location loc) {
        String p = "warps." + name.toLowerCase() + ".";
        dataConfig.set(p + "world", loc.getWorld().getName());
        dataConfig.set(p + "x",     loc.getX());
        dataConfig.set(p + "y",     loc.getY());
        dataConfig.set(p + "z",     loc.getZ());
        dataConfig.set(p + "yaw",   (double) loc.getYaw());
        dataConfig.set(p + "pitch", (double) loc.getPitch());
        save();
    }

    public Location getWarp(String name) {
        name = name.toLowerCase();
        if (!warpExists(name)) return null;
        String p     = "warps." + name + ".";
        String wName = dataConfig.getString(p + "world");
        World  world = Bukkit.getWorld(wName == null ? "" : wName);
        if (world == null) return null;
        return new Location(
                world,
                dataConfig.getDouble(p + "x"),
                dataConfig.getDouble(p + "y"),
                dataConfig.getDouble(p + "z"),
                (float) dataConfig.getDouble(p + "yaw"),
                (float) dataConfig.getDouble(p + "pitch")
        );
    }

    public void deleteWarp(String name) {
        dataConfig.set("warps." + name.toLowerCase(), null);
        save();
    }

    public List<String> getWarpNames() {
        ConfigurationSection sec = dataConfig.getConfigurationSection("warps");
        if (sec == null) return Collections.emptyList();
        return new ArrayList<>(sec.getKeys(false));
    }
}
