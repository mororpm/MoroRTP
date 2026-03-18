package net.morosmp.bounty;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    // Парсер Hex цветов (<#RRGGBB>) и стандартных &
    public static String color(String message, boolean useSmallCaps) {
        if (message == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        String colored = ChatColor.translateAlternateColorCodes('&', buffer.toString());
        return useSmallCaps ? sc(colored) : colored;
    }

    // Транслятор в Small Caps
    public static String sc(String text) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        boolean skip = false;
        for (char c : text.toCharArray()) {
            if (c == '§') { skip = true; sb.append(c); continue; }
            if (skip) { sb.append(c); skip = false; continue; }
            int idx = normal.indexOf(c);
            if (idx != -1) sb.append(small.charAt(idx));
            else sb.append(c);
        }
        return sb.toString();
    }

    // Парсер сумм (1k, 1m, 1b)
    public static double parseAmount(String input) throws NumberFormatException {
        input = input.toLowerCase().replace(",", "");
        double multiplier = 1.0;
        if (input.endsWith("k")) { multiplier = 1000.0; input = input.replace("k", ""); }
        else if (input.endsWith("m")) { multiplier = 1000000.0; input = input.replace("m", ""); }
        else if (input.endsWith("b")) { multiplier = 1000000000.0; input = input.replace("b", ""); }
        return Double.parseDouble(input) * multiplier;
    }
}