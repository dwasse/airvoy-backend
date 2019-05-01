package com.airvoy;

import com.airvoy.trading.ExchangeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Initializing database...");
        String url = "jdbc:mysql://localhost:3306/airvoydb?useSSL=false";
        String user = "admin";
        String password = "admin123";
        DatabaseManager databaseManager = new DatabaseManager(url, user, password);
        ExchangeManager exchangeManager = new ExchangeManager(databaseManager);
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

}
