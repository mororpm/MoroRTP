package net.morosmp.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final MoroRTP plugin;
    private final MiniMessage miniMessage;

    public ConfigManager(MoroRTP plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    // --- SMALL CAPS ENGINE ---
    private char toSmallCapsChar(char c) {
        switch (c) {
            case 'a':
                return 'ᴀ';
            case 'b':
                return 'ʙ';
            case 'c':
                return 'ᴄ';
            case 'd':
                return 'ᴅ';
            case 'e':
                return 'ᴇ';
            case 'f':
                return 'ғ';
            case 'g':
                return 'ɢ';
            case 'h':
                return 'ʜ';
            case 'i':
                return 'ɪ';
            case 'j':
                return 'ᴊ';
            case 'k':
                return 'ᴋ';
            case 'l':
                return 'ʟ';
            case 'm':
                return 'ᴍ';
            case 'n':
                return 'ɴ';
            case 'o':
                return 'ᴏ';
            case 'p':
                return 'ᴘ';
            case 'q':
                return 'ǫ';
            case 'r':
                return 'ʀ';
            case 's':
                return 's';
            case 't':
                return 'ᴛ';
            case 'u':
                return 'ᴜ';
            case 'v':
                return 'ᴠ';
            case 'w':
                return 'ᴡ';
            case 'x':
                return 'x';
            case 'y':
                return 'ʏ';
            case 'z':
                return 'ᴢ';
            default:
                return c;
        }
    }

    public String applySmallCaps(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean inTag = false;
        for (char c : text.toCharArray()) {
            if (c == '<')
                inTag = true;
            if (c == '>') {
                inTag = false;
                result.append(c);
                continue;
            }
            if (inTag) {
                result.append(c);
            } else {
                result.append(toSmallCapsChar(c));
            }
        }
        return result.toString();
    }
    // -------------------------

    public Component parseMessage(String key, String... placeholders) {
        String baseStr = getConfig().getString("messages." + key, "<red>Message " + key + " not found.");
        for (int i = 0; i < placeholders.length; i += 2) {
            String ph = placeholders[i];
            String val = placeholders[i + 1];
            baseStr = baseStr.replace(ph, val);
        }

        baseStr = applySmallCaps(baseStr); // Применяем фильтр
        return miniMessage.deserialize(baseStr);
    }

    public Component parseRawMessage(String text, String... placeholders) {
        String baseStr = text;
        for (int i = 0; i < placeholders.length; i += 2) {
            baseStr = baseStr.replace(placeholders[i], placeholders[i + 1]);
        }

        baseStr = applySmallCaps(baseStr); // Применяем фильтр
        return miniMessage.deserialize(baseStr);
    }

    public Sound getSound(String key) {
        String name = getConfig().getString("sounds." + key + ".name");
        if (name == null)
            return null;
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public float getSoundVolume(String key) {
        return (float) getConfig().getDouble("sounds." + key + ".volume", 1.0);
    }

    public float getSoundPitch(String key) {
        return (float) getConfig().getDouble("sounds." + key + ".pitch", 1.0);
    }

    public Particle getParticle() {
        String pStr = getConfig().getString("effects.particle", "PORTAL");
        try {
            return Particle.valueOf(pStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Particle.PORTAL;
        }
    }

    public int getParticleAmount() {
        return getConfig().getInt("effects.amount", 10);
    }
}