package com.cachebrowse.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton wrapper around a JDBC {@link Connection}.
 * <p>
 * The database is an H2 file based database located at
 * {@code ${project.build.directory}/cachebrowser.db}.  The directory is created if it does not exist.
 */
public class DatabaseConnection {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConnection.class);
    private static final String JDBC_URL = "jdbc:h2:file:./data/cachebrowser;AUTO_SERVER=TRUE";
    private static Connection connection;

    private DatabaseConnection() { /* util class */ }

    public static synchronized Connection getConnection() {
        if (connection == null) {
            try {
                connection = DriverManager.getConnection(JDBC_URL, "sa", "");
            } catch (SQLException e) {
                LOG.error("Failed to connect to H2 database", e);
                throw new RuntimeException(e);
            }
        }
        return connection;
    }
}
