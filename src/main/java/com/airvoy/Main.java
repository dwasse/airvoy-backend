package com.airvoy;

import com.airvoy.model.Account;
import com.airvoy.model.Market;
import com.airvoy.model.Order;
import com.airvoy.model.utils.LoggerFactory;
import com.airvoy.trading.ExchangeManager;

public class Main {

    private static LoggerFactory logger;

    public static void main(String[] args) {
//        configureLogging();
        LoggerFactory.startLogger("server.log");
        logger = new LoggerFactory("Main");
        logger.info("Initializing database...");
        String url = "jdbc:mysql://localhost:3306/airvoydb?useSSL=false";
        String user = "admin";
        String password = "admin123";
        DatabaseManager databaseManager = new DatabaseManager(url, user, password);
        addInitialMarkets(databaseManager);
        addInitialAccounts(databaseManager);
        ExchangeManager exchangeManager = new ExchangeManager(databaseManager);
        addInitialOrders(databaseManager, exchangeManager);
        logger.info("Starting server...");
        int port;
        try {
            port = Integer.parseInt(System.getenv("PORT"));
        } catch (NumberFormatException nfe) {
            port = 9001;
        }
        Server server = new Server(port, databaseManager, exchangeManager);
        server.start();
    }

    private static void configureLogging() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %3$s: %5$s%6$s%n");
    }

    public static void addInitialAccounts(DatabaseManager databaseManager) {
        long currentTime = System.currentTimeMillis();
        Account user1 = new Account("user1");
        databaseManager.addUser(user1.getUsername(), 1.23124, currentTime);
        Account user2 = new Account("user2");
        databaseManager.addUser(user2.getUsername(), 3.1234, currentTime);
        Account user3 = new Account("user3");
        databaseManager.addUser(user3.getUsername(), 2.123, currentTime);
    }

    public static void addInitialMarkets(DatabaseManager databaseManager) {
        long currentTime = System.currentTimeMillis();
        Market newMarket = new Market("trump-impeachment-2020", "TRUMP", (currentTime + (86400000 * 365)));
        databaseManager.addMarket(newMarket);
    }

    public static void addInitialOrders(DatabaseManager databaseManager, ExchangeManager exchangeManager) {
        Market market = new Market("TRUMP", databaseManager);
        Account user1 = new Account("user1");
        Order newOrder;
        newOrder = new Order(market, Order.BUY, .4, 1, user1, Order.Type.LIMIT);
        databaseManager.addOrderUpdate(newOrder);
        exchangeManager.submitOrder(newOrder, false);
        newOrder = new Order(market, Order.BUY, .3, 2, user1, Order.Type.LIMIT);
        databaseManager.addOrderUpdate(newOrder);
        exchangeManager.submitOrder(newOrder, false);
        newOrder = new Order(market, Order.BUY, .3, .5, user1, Order.Type.LIMIT);
        databaseManager.addOrderUpdate(newOrder);
        exchangeManager.submitOrder(newOrder, false);
        newOrder = new Order(market, Order.SELL, .5, 1, user1, Order.Type.LIMIT);
        databaseManager.addOrderUpdate(newOrder);
        exchangeManager.submitOrder(newOrder, false);
        newOrder = new Order(market, Order.SELL, .6, 2.5, user1, Order.Type.LIMIT);
        databaseManager.addOrderUpdate(newOrder);
        exchangeManager.submitOrder(newOrder, false);
        logger.info("Added initial data.");
    }

}
