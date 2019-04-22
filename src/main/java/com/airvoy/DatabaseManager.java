package com.airvoy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class DatabaseManager {

    private final static Logger logger = LogManager.getLogger(DatabaseManager.class);

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    public DatabaseManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        try {
            init();
        } catch (Exception e) {
            logger.warn("Error connecting to db: " + e.getMessage());
        }
    }

    private void init() throws Exception {
        connect();

        executeStatement("USE airvoydb;");
        executeStatement("DROP TABLE IF EXISTS Users, Markets, Orders, Trades;");
        executeStatement("CREATE TABLE Users(Username VARCHAR(20) PRIMARY KEY, Balance DOUBLE, CreationTime BIGINT);");
        executeStatement("CREATE TABLE Markets(Id BIGINT PRIMARY KEY AUTO_INCREMENT, Name VARCHAR(100), Expiry BIGINT, CreationTime BIGINT);");
        System.out.println("Initialized database.");

        // Add bootstrap data
        long currentTime = System.currentTimeMillis();
        addUser("user1", 1.23124, currentTime);
        addUser("user2", 3.1234, currentTime);
        addUser("user3", 2.123, currentTime);
        addMarket(0, "trump-impeachment-2020", (currentTime + (86400000 * 365)), currentTime);
        System.out.println("Added initial data.");
    }

    private void addUser(String username, double balance, long creationTime) {
        String command = "INSERT INTO Users(Username, Balance, CreationTime) VALUES(\""
                + username + "\", " + String.valueOf(balance) + ", " + String.valueOf(creationTime)
                + ")";
        try {
            executeStatement(command);
        } catch (Exception e) {
            logger.warn("Exception adding user: " + e.getMessage());
        }
    }

    public void addMarket(int id, String name, long expiry, long creationTime) {
        String command = "INSERT INTO Markets(Id, Name, Expiry, CreationTime) VALUES("
                + String.valueOf(id) + ", \"" + name + "\", " + String.valueOf(expiry) + ", "
                + String.valueOf(creationTime) + ")";
        try {
            executeStatement(command);
        } catch (Exception e) {
            logger.warn("Exception executing statement: " + e.getMessage());
        }
    }

    private void executeStatement(String command) throws Exception {
        PreparedStatement statement = connection.prepareStatement(command);
        statement.executeUpdate();
        System.out.println("Executed statement: " + command);
    }

    private void connect() throws Exception {
        System.out.println("Connecting to db: " + url);
        connection = DriverManager.getConnection(url, user, password);
    }

}
