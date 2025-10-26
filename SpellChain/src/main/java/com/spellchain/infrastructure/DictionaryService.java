package com.spellchain.infrastructure;

import com.spellchain.application.port.Dictionary;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

/**
 * Dictionary implementation backed by a read-only SQLite database.
 *
 * <p>This service opens a read-only JDBC connection to an existing SQLite dictionary and performs
 * all lookups via parameterized SQL queries for safety and simplicity. Inputs are normalized to
 * lower-case (Locale.ROOT). Prefix queries use SQLite LIKE with an explicit ESCAPE clause, and user
 * input is escaped to avoid wildcard interpretation.
 */
@Service
public class DictionaryService implements Dictionary {
  private static final Logger log = LoggerFactory.getLogger(DictionaryService.class);

  private static final String SQL_IS_WORD = "SELECT 1 FROM dict WHERE word = ? LIMIT 1";
  private static final String SQL_HAS_PREFIX = "SELECT 1 FROM dict WHERE word LIKE ? ESCAPE '\\' LIMIT 1";
  private static final String SQL_DEF_BY_WORD = "SELECT def FROM dict WHERE word = ?";

  private final String jdbcUrl;
  private final Object lock = new Object();
  
  private Connection conn;

  public DictionaryService(@Value("${spellchain.dictionary-jdbc-url}") String jdbcUrl) {
    this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "spellchain.dictionary-jdbc-url");
  }

  /**
   * Open a read-only SQLite connection and apply conservative PRAGMAs.
   *
   * <p>If the JDBC URL points to a file path, the file's existence is verified. The connection is
   * configured as read-only and set to auto-commit. The following PRAGMAs are set:
   * - query_only=ON
   * - busy_timeout=3000
   * - temp_store=MEMORY
   * - mmap_size=134217728
   * - cache_size=20000
   *
   * @throws Exception if connection or configuration fails
   */
  @PostConstruct
  public void load() throws Exception {
    long t0 = System.nanoTime();
    try {
      if (jdbcUrl.startsWith("jdbc:sqlite:")) {
        String path = jdbcUrl.substring("jdbc:sqlite:".length());
        if (!path.startsWith(":")) {
          Path db = Path.of(path).toAbsolutePath().normalize();
          if (Files.notExists(db)) throw new IllegalStateException("Dictionary DB not found: " + db);
        }
      }

      SQLiteConfig cfg = new SQLiteConfig();
      cfg.setReadOnly(true);
      cfg.setOpenMode(SQLiteOpenMode.READONLY);

      conn = DriverManager.getConnection(jdbcUrl, cfg.toProperties());
      conn.setReadOnly(true);
      conn.setAutoCommit(true);

      try (Statement s = conn.createStatement()) {
        s.execute("PRAGMA query_only=ON");
        s.execute("PRAGMA busy_timeout=3000");
        s.execute("PRAGMA temp_store=MEMORY");
        s.execute("PRAGMA mmap_size=134217728");
        s.execute("PRAGMA cache_size=20000");
      }

      long ms = (System.nanoTime() - t0) / 1_000_000;
      log.info("Dictionary DB ready ({} ms). Using SQL lookups only.", ms);
    } catch (Exception e) {
      close();
      throw e;
    }
  }

  /** Close the SQLite connection.*/
  @PreDestroy
  public void close() {
    synchronized (lock) {
      try {
        if (conn != null) conn.close();
      } catch (Exception ignore) {
        // ignore
      }
      conn = null;
    }
  }

  /**
   * Check whether the provided string is a known dictionary word.
   *
   * @param w string to check (may be null)
   * @return true if an word match exists
   */
  @Override
  public boolean isWord(String w) {
    String word = norm(w);
    if (word == null) return false;
    return exists(SQL_IS_WORD, word, "isWord");
  }

  /**
   * Check whether the provided string is a prefix of any word in the dictionary.
   *
   * @param p prefix to check (may be null)
   * @return true if there exists a word that begins with the given prefix
   */
  @Override
  public boolean hasPrefix(String p) {
    String prefix = norm(p);
    if (prefix == null) return false;
    String like = escapeLike(prefix) + "_%";
    return exists(SQL_HAS_PREFIX, like, "hasPrefix");
  }

  /**
   * Retrieve the definition for the given word.
   *
   * @param w word to look up (may be null)
   * @return definition string or a "No definition available." placeholder
   */
  @Override
  public String definition(String w) {
    String word = norm(w);
    if (word == null) return null;
    return queryString(SQL_DEF_BY_WORD, word, "definition");
  }

  /** 
   * Execute a simple existence check returning true if at least one row matches.
   * 
   * @param sql SQL query with one parameter
   * @param param parameter value
   * @param op operation name for logging
   * @return true if at least one row matches
   */
  private boolean exists(String sql, String param, String op) {
    synchronized (lock) {
      if (conn == null) return false;
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, param);
        try (ResultSet rs = ps.executeQuery()) {
          return rs.next();
        }
      } catch (SQLException e) {
        log.debug("{} lookup failed for '{}': {}", op, param, e.getMessage());
        return false;
      }
    }
  }

  /** 
   * Execute a single-column string query and return the first result or null.
   * 
   * @param sql SQL query with one parameter
   * @param param parameter value
   * @param op operation name for logging
   * @return query result string or null
   */
  private String queryString(String sql, String param, String op) {
    synchronized (lock) {
      if (conn == null) return null;
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, param);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return rs.getString(1);
          }
          return null;
        }
      } catch (SQLException e) {
        log.debug("{} lookup failed for '{}': {}", op, param, e.getMessage());
        return null;
      }
    }
  }

  /** Normalize input string by lower-casing (Locale.ROOT). */
  private static String norm(String s) {
    if (s == null) return null;
    String n = s.toLowerCase(Locale.ROOT);
    return n.isEmpty() ? null : n;
  }

  /** Escape special LIKE wildcard characters in user input. */
  private static String escapeLike(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}