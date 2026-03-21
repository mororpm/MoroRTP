package net.morosmp.stats;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ColorUtil — translates &#RRGGBB HEX codes and legacy & color codes into
 * the Minecraft legacy §-format that FastBoard (and the underlying scoreboard
 * packets) understand.
 *
 * <p>FastBoard operates on raw legacy-color strings. Paper's Adventure API
 * cannot be used here because FastBoard builds the net.minecraft packets
 * directly — it bypasses Adventure entirely.
 *
 * <p>Translation order:
 *   1. &#RRGGBB  →  §x§R§R§G§G§B§B  (Spigot RGB format)
 *   2. &X        →  §X               (classic color/format codes)
 */
public final class ColorUtil {

    // Matches &#RRGGBB exactly (6 hex digits)
    private static final Pattern HEX_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {}

    /**
     * Converts a string containing {@code &#RRGGBB} and {@code &X} codes
     * into the legacy §-format expected by FastBoard.
     *
     * @param input raw text with color codes
     * @return formatted string ready for packet injection
     */
    public static String translate(String input) {
        if (input == null || input.isEmpty()) return "";

        // Step 1: replace &#RRGGBB with §x§R§R§G§G§B§B
        Matcher hex = HEX_PATTERN.matcher(input);
        StringBuilder buffer = new StringBuilder();
        while (hex.find()) {
            String code = hex.group(1);        // e.g. "00EAFF"
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : code.toCharArray()) {
                replacement.append('§').append(c);
            }
            hex.appendReplacement(buffer, replacement.toString());
        }
        hex.appendTail(buffer);

        // Step 2: replace remaining &X codes (colors, §r reset, etc.)
        return buffer.toString().replace('&', '§');
    }
}
