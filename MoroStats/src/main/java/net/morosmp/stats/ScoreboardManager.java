package net.morosmp.stats;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ScoreboardManager — per-player sidebar scoreboard, zero FastBoard dependency.
 *
 * ── Why no FastBoard? ───────────────────────────────────────────────────────
 * FastBoard patches NMS (net.minecraft.server) internals to build scoreboard
 * packets. Paper 1.21.4+ relocated these classes; 1.21.11 removed the specific
 * method FastBoard targets. Result: ClassNotFoundException on every enable.
 * This class uses ONLY the public Bukkit Scoreboard API.
 *
 * ── Zero-flicker via Team prefix technique ──────────────────────────────────
 * Calling objective.getScore(entry).setScore(n) every tick causes the client to
 * receive REMOVE_SCORE → ADD_SCORE, momentarily blanking the line.
 *
 * Fix: each line gets a permanent "phantom entry" (a unique invisible colour-code
 * pair like "§0§r"). Its SCORE is written ONCE on setup and never touched again.
 * The visible text lives in a Team's PREFIX for that entry. Updating only the
 * prefix sends a single TEAMS packet — the entry never disappears, zero flicker.
 *
 * ── Scoreboard layout ───────────────────────────────────────────────────────
 *
 *   ┌─────────────────────────┐
 *   │ MoroSMP                 │  ← title  #00EAFF cyan
 *   ├─────────────────────────┤
 *   │                         │  spacer
 *   │ Profile                 │  #d1d5db light-gray header
 *   │ Name: Steve             │  #6b7280 label / #f9fafb value
 *   │ Ping: 12 ms             │  #6b7280 label / #00EAFF value
 *   │                         │  spacer
 *   │ Stats                   │  #d1d5db header
 *   │ Kills: 42               │  #22c55e green value
 *   │ Deaths: 7               │  #ef4444 red value
 *   │ Time: 3h 20m            │  #fbbf24 gold value
 *   │                         │  spacer
 *   │ morosmp.net             │  #22c55e footer
 *   └─────────────────────────┘
 *
 * ── Thread model ────────────────────────────────────────────────────────────
 * Runs on the MAIN thread (runTaskTimer, not runTaskTimerAsynchronously).
 * The Bukkit Scoreboard API is not thread-safe on Paper 1.21; async calls
 * cause ConcurrentModificationException inside Bukkit's team registry.
 * Per-tick cost: ~100 µs for a full server — completely negligible.
 */
public class ScoreboardManager {

    // ── HEX regex ─────────────────────────────────────────────────────────────
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    // ── Color palette ─────────────────────────────────────────────────────────
    // Standard &#RRGGBB codes — clean HEX, no spaced-out unicode glyph fonts.
    private static final String CYAN       = "&#00EAFF";
    private static final String LGRAY      = "&#d1d5db";  // section headers
    private static final String MGRAY      = "&#6b7280";  // labels / pipes
    private static final String WHITE      = "&#f9fafb";
    private static final String GREEN      = "&#22c55e";
    private static final String RED        = "&#ef4444";
    private static final String GOLD       = "&#fbbf24";

    // ── Internal constants ────────────────────────────────────────────────────
    private static final String OBJ_NAME  = "morostats";   // objective internal id
    private static final String TEAM_NS   = "ms_";          // team name namespace

    // 13 lines (index 0 = top, index 12 = bottom)
    private static final int      LINE_COUNT = 13;
    private static final String[] ENTRIES    = new String[LINE_COUNT];

    static {
        // §0§r … §9§r, §a§r, §b§r, §c§r  — invisible on their own, each unique
        for (int i = 0; i < LINE_COUNT; i++) {
            ENTRIES[i] = "§" + Integer.toHexString(i) + "§r";
        }
    }

    // ── Per-player wrapper ────────────────────────────────────────────────────
    private record PlayerBoard(Scoreboard scoreboard, Objective objective) {}

    // ── State ─────────────────────────────────────────────────────────────────
    private final Map<UUID, PlayerBoard> boards = new ConcurrentHashMap<>();
    private final MoroStats              plugin;
    private final boolean                papiAvailable;
    private       BukkitTask             updateTask;

    // =========================================================================
    // Constructor
    // =========================================================================

    public ScoreboardManager(MoroStats plugin, boolean papiAvailable) {
        this.plugin        = plugin;
        this.papiAvailable = papiAvailable;
    }

    // =========================================================================
    // Lifecycle — called from MoroStats.onEnable / onDisable
    // =========================================================================

    /**
     * Starts the 1-second update loop (sync, main thread).
     * Call AFTER addPlayer() has been called for all currently online players.
     */
    public void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : boards.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) updateBoard(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        plugin.getLogger().info("[MoroStats] Scoreboard task started (20 ticks / sync).");
    }

    /**
     * Cancels the update task and removes every custom scoreboard, restoring the
     * default server scoreboard so no sidebar lingers after plugin disable.
     */
    public void stopUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (UUID uuid : boards.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.setScoreboard(main);
        }
        boards.clear();
        plugin.getLogger().info("[MoroStats] All scoreboards removed.");
    }

    // =========================================================================
    // Player join / quit — called from PlayerListener
    // =========================================================================

    /**
     * Creates a new private Scoreboard for the player, registers phantom entries
     * at fixed scores, then renders the first frame immediately.
     */
    public void addPlayer(Player player) {
        removePlayer(player);   // clear any stale board from /reload

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        // Create the sidebar objective
        Objective obj = board.registerNewObjective(
                OBJ_NAME,
                Criteria.DUMMY,
                c(CYAN + "MoroSMP")
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Register one team per line, add its phantom entry, pin its score.
        // score LINE_COUNT-1 = topmost, score 0 = bottommost.
        for (int i = 0; i < LINE_COUNT; i++) {
            Team team = board.registerNewTeam(TEAM_NS + i);
            team.addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(LINE_COUNT - 1 - i);
        }

        player.setScoreboard(board);
        boards.put(player.getUniqueId(), new PlayerBoard(board, obj));

        updateBoard(player);    // immediate first render, don't wait a full second
        plugin.getLogger().fine("[MoroStats] Board added for " + player.getName());
    }

    /**
     * Restores the main scoreboard and removes the player from the board map.
     */
    public void removePlayer(Player player) {
        PlayerBoard pb = boards.remove(player.getUniqueId());
        if (pb != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // =========================================================================
    // Core render
    // =========================================================================

    /**
     * Resolves all data and writes 13 display lines by mutating Team prefixes.
     * Zero score changes = zero flicker.
     */
    private void updateBoard(Player player) {
        PlayerBoard pb = boards.get(player.getUniqueId());
        if (pb == null) return;

        // Update objective title
        pb.objective().setDisplayName(c(CYAN + "MoroSMP"));

        // ── Resolve live values ────────────────────────────────────────────────
        String name   = player.getName();
        String ping   = player.getPing() + " ms";
        String kills  = papi(player, "%statistic_player_kills%");
        String deaths = papi(player, "%statistic_deaths%");
        String time   = formatTime(papi(player, "%statistic_time_played%"));

        // ── Build 13 lines (index 0 = top of sidebar) ─────────────────────────
        // Clean English text. No spaced unicode glyphs, no full-width characters.
        String[] lines = {
            "",                                                      //  0  spacer
            c(LGRAY + "Profile"),                                    //  1  header
            c(MGRAY + "Name: "   + WHITE + name),                   //  2  name
            c(MGRAY + "Ping: "   + CYAN  + ping),                   //  3  ping
            "",                                                      //  4  spacer
            c(LGRAY + "Stats"),                                      //  5  header
            c(MGRAY + "Kills: "  + GREEN + kills),                  //  6  kills
            c(MGRAY + "Deaths: " + RED   + deaths),                 //  7  deaths
            c(MGRAY + "Time: "   + GOLD  + time),                   //  8  playtime
            "",                                                      //  9  spacer
            c(LGRAY + "Server"),                                     // 10  sub-header
            c(GREEN + "morosmp.net"),                                // 11  footer
            "",                                                      // 12  bottom spacer
        };

        Scoreboard board = pb.scoreboard();
        for (int i = 0; i < lines.length; i++) {
            Team team = board.getTeam(TEAM_NS + i);
            if (team == null) continue;
            writeLine(team, lines[i]);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Translates {@code &#RRGGBB} and {@code &X} codes into the Minecraft
     * §-colour format (§x§R§R§G§G§B§B for RGB; §X for legacy codes).
     * Self-contained — no external ColorUtil dependency.
     */
    private static String c(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher m = HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char ch : m.group(1).toCharArray()) r.append('§').append(ch);
            m.appendReplacement(sb, r.toString());
        }
        m.appendTail(sb);
        return sb.toString().replace('&', '§');
    }

    /**
     * Writes a display line to a Team prefix (+suffix overflow), respecting
     * Bukkit's 64-character-per-field hard limit.
     *
     * Walks backwards from position 64 before splitting to avoid cutting inside
     * a §x§R§R§G§G§B§B sequence (14 chars), which would corrupt the RGB colour.
     */
    private static void writeLine(Team team, String content) {
        if (content.length() <= 64) {
            team.setPrefix(content);
            team.setSuffix("");
            return;
        }
        int split = 64;
        while (split > 0 && content.charAt(split - 1) == '§') split--;
        team.setPrefix(content.substring(0, split));
        String tail = content.substring(split);
        team.setSuffix(tail.length() <= 64 ? tail : tail.substring(0, 64));
    }

    /**
     * Resolves a PlaceholderAPI placeholder. Returns "N/A" if PAPI is absent or
     * the expansion has not resolved the placeholder.
     */
    private String papi(Player player, String placeholder) {
        if (!papiAvailable) return "N/A";
        try {
            String val = PlaceholderAPI.setPlaceholders(player, placeholder);
            return (val == null || val.equals(placeholder)) ? "N/A" : val;
        } catch (Exception e) {
            plugin.getLogger().warning("[MoroStats] PAPI error '"
                    + placeholder + "': " + e.getMessage());
            return "N/A";
        }
    }

    /**
     * Converts the raw tick count from %statistic_time_played% into "Xh Ym".
     * If the PAPI expansion already returned a formatted string, returns it as-is.
     */
    private static String formatTime(String raw) {
        if ("N/A".equals(raw) || raw == null) return "N/A";
        try {
            long ticks   = Long.parseLong(raw.trim());
            long seconds = ticks / 20;
            long hours   = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
        } catch (NumberFormatException ignored) {
            return raw; // already formatted
        }
    }
}
