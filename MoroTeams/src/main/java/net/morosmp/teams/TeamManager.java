package net.morosmp.teams;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * TeamManager — YAML-backed team storage with in-memory invite tracking.
 *
 * YAML layout:
 *   teams:
 *     <name>:
 *       owner: <uuid>
 *       members: [uuid, uuid, ...]   ← owner IS in this list
 */
public class TeamManager {

    private final TeamsPlugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    /** In-memory: invitee UUID → team name they were invited to. */
    private final Map<UUID, String> pendingInvites = new HashMap<>();

    public TeamManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        dataFile   = new File(plugin.getDataFolder(), "teams.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try { dataConfig.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save teams.yml", e);
        }
    }

    // ── Queries ──────────────────────────────────────────────────────

    /** Returns the name of the team this player belongs to, or null. */
    public String getTeamOf(UUID uuid) {
        ConfigurationSection teams = dataConfig.getConfigurationSection("teams");
        if (teams == null) return null;
        for (String name : teams.getKeys(false)) {
            List<String> members = dataConfig.getStringList("teams." + name + ".members");
            if (members.contains(uuid.toString())) return name;
        }
        return null;
    }

    public boolean teamExists(String name) {
        return dataConfig.contains("teams." + name);
    }

    public boolean isInTeam(UUID uuid) {
        return getTeamOf(uuid) != null;
    }

    public boolean areTeammates(UUID a, UUID b) {
        String ta = getTeamOf(a);
        return ta != null && ta.equals(getTeamOf(b));
    }

    public boolean isOwner(String teamName, UUID uuid) {
        return uuid.toString().equals(dataConfig.getString("teams." + teamName + ".owner"));
    }

    public List<UUID> getMembers(String teamName) {
        List<UUID> result = new ArrayList<>();
        for (String s : dataConfig.getStringList("teams." + teamName + ".members")) {
            try { result.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        return result;
    }

    // ── Mutations ────────────────────────────────────────────────────

    public void createTeam(String name, UUID ownerUuid) {
        dataConfig.set("teams." + name + ".owner", ownerUuid.toString());
        dataConfig.set("teams." + name + ".members", List.of(ownerUuid.toString()));
        save();
    }

    public void addMember(String teamName, UUID uuid) {
        List<String> members = new ArrayList<>(
                dataConfig.getStringList("teams." + teamName + ".members"));
        if (!members.contains(uuid.toString())) {
            members.add(uuid.toString());
            dataConfig.set("teams." + teamName + ".members", members);
            save();
        }
    }

    /**
     * Removes a member. If the owner leaves and others remain, ownership
     * transfers to the next member. If the last member leaves, the team
     * is disbanded entirely.
     */
    public void removeMember(String teamName, UUID uuid) {
        List<String> members = new ArrayList<>(
                dataConfig.getStringList("teams." + teamName + ".members"));
        members.remove(uuid.toString());

        if (members.isEmpty()) {
            // Last member → disband
            dataConfig.set("teams." + teamName, null);
        } else if (isOwner(teamName, uuid)) {
            // Owner left → transfer to next member
            dataConfig.set("teams." + teamName + ".owner", members.get(0));
            dataConfig.set("teams." + teamName + ".members", members);
        } else {
            dataConfig.set("teams." + teamName + ".members", members);
        }
        save();
    }

    // ── Invite tracking (in-memory only) ─────────────────────────────

    public void addInvite(UUID invitee, String teamName) {
        pendingInvites.put(invitee, teamName);
    }

    public String getPendingInvite(UUID invitee) {
        return pendingInvites.get(invitee);
    }

    public void removeInvite(UUID invitee) {
        pendingInvites.remove(invitee);
    }
}
