package com.airvoy;

import com.airvoy.model.Market;
import com.airvoy.trading.MatchingEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        System.out.println("Initializing database...");
        String url = "jdbc:mysql://localhost:3306/airvoydb?useSSL=false";
        String user = "admin";
        String password = "admin123";
        DatabaseManager databaseManager = new DatabaseManager(url, user, password);
        Map<String, MatchingEngine> matchingEngineMap = generateMatchingEngines(databaseManager);
        System.out.println("Generated matching engines: " + matchingEngineMap.toString());
        System.out.println("Starting server...");
        int port;
        try {
            port = Integer.parseInt(System.getenv("PORT"));
        } catch (NumberFormatException nfe) {
            port = 9001;
        }
        Server server = new Server(port, databaseManager);
        server.start();
    }

    private static Map<String, MatchingEngine> generateMatchingEngines(DatabaseManager databaseManager) {
        ResultSet resultSet = databaseManager.executeQuery("SELECT Name, Symbol, Expiry FROM Markets");
        Map<String, MatchingEngine> matchingEngineMap = new HashMap<>();
        try {
            while (resultSet.next()) {
                String marketName = resultSet.getString("Name");
                String symbol = resultSet.getString("Symbol");
                long expiry = resultSet.getLong("Expiry");
                System.out.println("Got market name: " + marketName);
                matchingEngineMap.put(marketName, new MatchingEngine(new Market(marketName, symbol, expiry)));
            }
        } catch (SQLException e) {
            logger.warn("Error generating JSON response: " + e.getMessage());
        }
        return matchingEngineMap;
    }

}
