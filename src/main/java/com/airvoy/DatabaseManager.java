package com.airvoy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DatabaseManager {

    private final static Logger logger = LogManager.getLogger(DatabaseManager.class);

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;
    private int entryCount = 0;

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
        executeStatement("CREATE TABLE Markets(Id BIGINT PRIMARY KEY AUTO_INCREMENT, Name VARCHAR(100), Symbol VARCHAR(5), Expiry BIGINT, CreationTime BIGINT);");
        executeStatement("CREATE TABLE Orders(Id BIGINT PRIMARY KEY AUTO_INCREMENT, Symbol VARCHAR(5), Type VARCHAR(20), Price DOUBLE, Amount DOUBLE, OrderTime BIGINT);");
        executeStatement("CREATE TABLE Trades(Id BIGINT PRIMARY KEY AUTO_INCREMENT, Maker VARCHAR(20), Taker VARCHAR(20), Price DOUBLE, Amount DOUBLE, Fee DOUBLE, TradeTime BIGINT);");
        System.out.println("Initialized database.");

        // Add bootstrap data
        long currentTime = System.currentTimeMillis();
        addUser("user1", 1.23124, currentTime);
        addUser("user2", 3.1234, currentTime);
        addUser("user3", 2.123, currentTime);
        addMarket(entryCount, "trump-impeachment-2020", "TRUMP", (currentTime + (86400000 * 365)), currentTime);
        addOrder(entryCount, "TRUMP", "limit", .4, 1, currentTime);
        addOrder(entryCount, "TRUMP", "limit", .2, 3, currentTime);
        addOrder(entryCount, "TRUMP", "limit", .5, -1, currentTime);
        addOrder(entryCount, "TRUMP", "limit", .6, -1.5, currentTime);
        addTrade(entryCount, "user1", "user2", .5, .5, 0, currentTime);
        System.out.println("Added initial data.");
    }

    private void addUser(String username, double balance, long creationTime) {
        String command = "INSERT INTO Users(Username, Balance, CreationTime) VALUES(\""
                + username + "\", " + String.valueOf(balance) + ", " + String.valueOf(creationTime)
                + ")";
        executeStatement(command);
    }

    public void addMarket(int id, String name, String symbol, long expiry, long creationTime) {
        String command = "INSERT INTO Markets(Id, Name, Symbol, Expiry, CreationTime) VALUES("
                + String.valueOf(id) + ", \"" + name + "\", \"" + symbol + "\", " + String.valueOf(expiry) + ", "
                + String.valueOf(creationTime) + ")";
        executeStatement(command);
    }

    private void addOrder(int id, String symbol, String type, double price, double amount, long timestamp) {
        String command = "INSERT INTO Orders(Id, Symbol, Type, Price, Amount, OrderTime) VALUES("
                + String.valueOf(id) + ", \"" + symbol + "\", \"" + type + "\", " + String.valueOf(price) + ", "
                + String.valueOf(amount) + ", " + String.valueOf(timestamp) + ")";
        executeStatement(command);
    }

    private void addTrade(int id, String maker, String taker, double price, double amount, double fee, long timestamp) {
        String command = "INSERT INTO Trades(Id, Maker, Taker, Price, Amount, Fee, TradeTime) VALUES("
                + String.valueOf(id) + ", \"" + maker + "\", \"" + taker + "\", " + String.valueOf(price) + ", "
                + String.valueOf(amount) + ", " + String.valueOf(fee) + ", " + String.valueOf(timestamp) + ")";
        System.out.println("Command: " + command);
        executeStatement(command);
    }

    public ResultSet executeQuery(String query) {
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            System.out.println("Executed query: " + query);
            return statement.executeQuery();
        } catch (Exception e) {
            logger.warn("Exception executing statement: " + e.getMessage());
        }
        return null;
    }

    public void executeStatement(String command) {
        try {
            PreparedStatement statement = connection.prepareStatement(command);
            statement.executeUpdate();
            System.out.println("Executed statement: " + command);
        } catch (Exception e) {
            logger.warn("Exception executing statement: " + e.getMessage());
        }
        entryCount++;
    }

    private void connect() throws Exception {
        System.out.println("Connecting to db: " + url);
        connection = DriverManager.getConnection(url, user, password);
    }

}
