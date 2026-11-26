package dev.spacepunk.prison.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.spacepunk.prison.api.Currency;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite persistence for profiles. Blocking: call ONLY from the storage executor, never the main thread.
 * Schema is one row per player; module data is a JSON object of {moduleKey: jsonString}.
 */
public final class ProfileStorage implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private final Connection connection;

    public ProfileStorage(File dataFolder) throws SQLException {
        dataFolder.mkdirs();
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("""
                CREATE TABLE IF NOT EXISTS profiles (
                  uuid TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  credits INTEGER NOT NULL DEFAULT 0,
                  tokens INTEGER NOT NULL DEFAULT 0,
                  shards INTEGER NOT NULL DEFAULT 0,
                  grade INTEGER NOT NULL DEFAULT 1,
                  prestige INTEGER NOT NULL DEFAULT 0,
                  module_data TEXT NOT NULL DEFAULT '{}'
                )""");
        }
    }

    public PlayerProfile loadOrCreate(UUID uuid, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM profiles WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                PlayerProfile p = new PlayerProfile(uuid, name);
                if (rs.next()) {
                    p.balance(Currency.CREDITS, rs.getLong("credits"));
                    p.balance(Currency.TOKENS, rs.getLong("tokens"));
                    p.balance(Currency.SHARDS, rs.getLong("shards"));
                    p.grade(rs.getInt("grade"));
                    p.prestige(rs.getInt("prestige"));
                    Map<String, String> md = GSON.fromJson(rs.getString("module_data"), MAP_TYPE);
                    if (md != null) md.forEach(p::moduleData);
                }
                return p;
            }
        }
    }

    /** Snapshot must be taken on the main thread; see {@link Snapshot#of}. */
    public void save(Snapshot s) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO profiles (uuid, name, credits, tokens, shards, grade, prestige, module_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, credits=excluded.credits, tokens=excluded.tokens,
                  shards=excluded.shards, grade=excluded.grade, prestige=excluded.prestige, module_data=excluded.module_data
                """)) {
            ps.setString(1, s.uuid.toString());
            ps.setString(2, s.name);
            ps.setLong(3, s.credits);
            ps.setLong(4, s.tokens);
            ps.setLong(5, s.shards);
            ps.setInt(6, s.grade);
            ps.setInt(7, s.prestige);
            ps.setString(8, s.moduleJson);
            ps.executeUpdate();
        }
    }

    @Override public void close() throws SQLException { connection.close(); }

    /** Immutable copy of a profile, safe to hand to the storage thread. */
    public record Snapshot(UUID uuid, String name, long credits, long tokens, long shards,
                           int grade, int prestige, String moduleJson) {
        public static Snapshot of(PlayerProfile p) {
            return new Snapshot(p.uuid(), p.lastName(), p.balance(Currency.CREDITS), p.balance(Currency.TOKENS),
                    p.balance(Currency.SHARDS), p.grade(), p.prestige(), GSON.toJson(p.allModuleData()));
        }
    }
}
