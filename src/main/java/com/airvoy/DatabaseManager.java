package com.airvoy;

import com.airvoy.model.Market;
import com.airvoy.model.Order;
import com.airvoy.model.Trade;
import com.airvoy.model.utils.LoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DatabaseManager {

    private final static LoggerFactory logger = new LoggerFactory("DatabaseManager");

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
        executeStatement("DROP TABLE IF EXISTS Users, Markets, OrderUpdates, Orderbooks, Trades;");
        executeStatement("CREATE TABLE Users(Username VARCHAR(20) PRIMARY KEY, Balance DOUBLE, " +
                "CreationTime BIGINT);");
        executeStatement("CREATE TABLE Markets(Id VARCHAR(100) PRIMARY KEY, Name VARCHAR(100), " +
                "Symbol VARCHAR(5), Expiry BIGINT, CreationTime BIGINT);");
        executeStatement("CREATE TABLE OrderUpdates(SequenceNumber BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "Id VARCHAR(100), Username VARCHAR(100), Symbol VARCHAR(5), Type VARCHAR(20), Price DOUBLE, " +
                "Amount DOUBLE, OrderTime BIGINT);");
        executeStatement("CREATE TABLE Orderbooks(Id VARCHAR(100) PRIMARY KEY, Symbol VARCHAR(100), " +
                "Username VARCHAR(100), Price DOUBLE, Amount DOUBLE)");
        executeStatement("CREATE TABLE Trades(Id VARCHAR(100) PRIMARY KEY, Maker VARCHAR(20), " +
                "Taker VARCHAR(20), Price DOUBLE, Amount DOUBLE, Fee DOUBLE, TradeTime BIGINT);");
        logger.info("Initialized database.");
    }

    public void addUser(String username, double balance, long creationTime) {
        String command = "INSERT INTO Users(Username, Balance, CreationTime) VALUES(\""
                + username + "\", " + String.valueOf(balance) + ", " + String.valueOf(creationTime)
                + ")";
        executeStatement(command);
    }

    public void addMarket(Market market) {
        String command = "INSERT INTO Markets(Id, Name, Symbol, Expiry, CreationTime) VALUES("
                + "\"" + market.getId() + "\", \"" + market.getName() + "\", \"" + market.getSymbol() + "\", "
                + String.valueOf(market.getExpiry()) + ", " + String.valueOf(market.getCreationTime()) + ")";
        logger.info("Market command: " + command);
        executeStatement(command);
    }

    public void addOrderUpdate(Order order) {
        String command = "INSERT INTO OrderUpdates(Id, Username, Symbol, Type, Price, Amount, OrderTime) VALUES("
                + "\"" + order.getId() + "\", \"" + order.getAccount().getUsername() + "\", \"" + order.getSymbol()
                + "\", \"" + order.getTypeString() + "\", " + String.valueOf(order.getPrice()) + ", "
                + String.valueOf(order.getSide() * order.getAmount()) + ", "
                + String.valueOf(order.getTimestamp()) + ")";
        executeStatement(command);
        logger.info("Executed orderbook update command: " + command);
        command = "INSERT INTO Orderbooks (Id, Username, Symbol, Price, Amount) VALUES("
                + "\"" + order.getId() + "\", \"" + order.getAccount().getUsername() + "\", \"" + order.getSymbol()
                + "\", " + String.valueOf(order.getPrice()) + ", " + String.valueOf(order.getSide() * order.getAmount())
                + ") ON DUPLICATE KEY UPDATE Price=" + String.valueOf(order.getPrice())
                + ", Amount=" + String.valueOf(order.getSide() * order.getAmount());
        executeStatement(command);
        logger.info("Executed orderbook command: " + command);
    }

    public void addTrade(Trade trade) {
        String command = "INSERT INTO Trades(Id, Maker, Taker, Price, Amount, Fee, TradeTime) VALUES("
                + "\"" + trade.getId() + "\", \"" + trade.getMakerAccount().getUsername() + "\", \""
                + trade.getTakerAccount().getUsername() + "\", " + String.valueOf(trade.getPrice()) + ", "
                + String.valueOf(trade.getAmount()) + ", " + String.valueOf(trade.getFee()) + ", "
                + String.valueOf(trade.getTimestamp()) + ")";
        logger.info("Command: " + command);
        executeStatement(command);
    }

    public ResultSet executeQuery(String query) {
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            logger.info("Executed query: " + query);
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
            logger.info("Executed statement: " + command);
        } catch (Exception e) {
            logger.warn("Exception executing statement: " + e.getMessage());
        }
        entryCount++;
    }

    private void connect() throws Exception {
        logger.info("Connecting to db: " + url);
        connection = DriverManager.getConnection(url, user, password);
    }

}
