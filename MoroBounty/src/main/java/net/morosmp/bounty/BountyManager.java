package net.morosmp.bounty;
import org.bukkit.Bukkit; import org.bukkit.entity.Player; import java.sql.*; import java.util.UUID;
public class BountyManager {
    private final MoroBounty plugin; private final Database db;
    public BountyManager(MoroBounty plugin) { this.plugin = plugin; this.db = new Database(); this.db.connect(); }
    public Database getDb() { return db; }
    public double getTotalBounty(UUID target) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT SUM(amount) as total FROM active_bounties WHERE target_uuid = ?")) {
            ps.setString(1, target.toString()); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("total");
        } catch (SQLException e) { } return 0.0;
    }
    public double getMinBountyFor(UUID target) { return Math.max(100.0, getTotalBounty(target) * 0.05); }
    public void addSmartContract(UUID sponsor, UUID target, double amount) {
        long expireTime = System.currentTimeMillis() + (3L * 24 * 60 * 60 * 1000);
        try (PreparedStatement ps = db.getConnection().prepareStatement("INSERT INTO active_bounties (sponsor_uuid, target_uuid, amount, expire_time) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, sponsor.toString()); ps.setString(2, target.toString()); ps.setDouble(3, amount); ps.setLong(4, expireTime); ps.executeUpdate();
        } catch (SQLException e) { }
    }
    public void clearBounties(UUID target) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM active_bounties WHERE target_uuid = ?")) {
            ps.setString(1, target.toString()); ps.executeUpdate();
        } catch (SQLException e) { }
    }
    public boolean isSameIP(Player p1, Player p2) {
        if (p1.getAddress() == null || p2.getAddress() == null) return false;
        return p1.getAddress().getAddress().getHostAddress().equals(p2.getAddress().getAddress().getHostAddress());
    }
    public int getKillCount(UUID killer, UUID victim) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT count FROM kill_history WHERE killer_victim = ?")) {
            ps.setString(1, killer.toString() + ":" + victim.toString()); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("count");
        } catch (SQLException e) { } return 0;
    }
    public void addKillRecord(UUID killer, UUID victim) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("INSERT OR REPLACE INTO kill_history (killer_victim, count) VALUES (?, ?)")) {
            ps.setString(1, killer.toString() + ":" + victim.toString()); ps.setInt(2, getKillCount(killer, victim) + 1); ps.executeUpdate();
        } catch (SQLException e) { }
    }
    public void processExpiredBounties() {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT * FROM active_bounties WHERE expire_time < ?")) {
            ps.setLong(1, System.currentTimeMillis()); ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                plugin.getVaultHook().deposit(Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("sponsor_uuid"))), rs.getDouble("amount") * 0.80);
                try (PreparedStatement del = db.getConnection().prepareStatement("DELETE FROM active_bounties WHERE id = ?")) { del.setInt(1, rs.getInt("id")); del.executeUpdate(); }
            }
        } catch (SQLException e) { }
    }
}