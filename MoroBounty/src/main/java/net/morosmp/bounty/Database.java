package net.morosmp.bounty;
import java.io.File; import java.sql.*;
public class Database {
    private Connection connection;
    public void connect() {
        try {
            File folder = new File("U:\\McServerData"); if (!folder.exists()) folder.mkdirs();
            Class.forName("org.sqlite.JDBC"); connection = DriverManager.getConnection("jdbc:sqlite:U:\\McServerData\\moro_bounty.db");
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS active_bounties (id INTEGER PRIMARY KEY AUTOINCREMENT, sponsor_uuid TEXT, target_uuid TEXT, amount DOUBLE, expire_time LONG)");
                s.execute("CREATE TABLE IF NOT EXISTS kill_history (killer_victim TEXT PRIMARY KEY, count INTEGER)");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    public Connection getConnection() {
        try { if (connection == null || connection.isClosed()) connect(); } catch (SQLException e) { connect(); }
        return connection;
    }
}