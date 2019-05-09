package com.airvoy.trading;

import com.airvoy.DatabaseManager;
import com.airvoy.model.Market;
import com.airvoy.model.Order;
import com.airvoy.model.utils.LoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.json.simple.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ExchangeManager {

    private final static LoggerFactory logger = new LoggerFactory("ExchangeManager");

    private DatabaseManager databaseManager;
    private Map<String, MatchingEngine> matchingEngineMap = new HashMap<>();
    private Set<WebSocket> connections = new HashSet<>();

    public ExchangeManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        generateMatchingEngines();
    }

    public void setConnections(Set<WebSocket> connections) {
        this.connections = connections;
    }

    private void generateMatchingEngines() {
        ResultSet resultSet = databaseManager.executeQuery("SELECT Name, Symbol, Expiry FROM Markets");
        try {
            while (resultSet.next()) {
                String marketName = resultSet.getString("Name");
                String symbol = resultSet.getString("Symbol");
                long expiry = resultSet.getLong("Expiry");
                matchingEngineMap.put(symbol, new MatchingEngine(new Market(marketName, symbol, expiry), databaseManager));
            }
        } catch (SQLException e) {
            logger.warn("Error generating JSON response: " + e.getMessage());
        }
        for (MatchingEngine matchingEngine: matchingEngineMap.values()) {
            matchingEngine.setMatchingEnginePointers(matchingEngineMap);
        }
    }

    public void generateMatchingEngine(String symbol) {
        if (!matchingEngineMap.containsKey(symbol)) {
            Market market = Market.fromSymbol(databaseManager, symbol);
            matchingEngineMap.put(symbol, new MatchingEngine(market, databaseManager));
        } else {
            logger.warn("Matching engine already present for symbol " + symbol);
        }
    }

    public MatchingEngine getMatchingEngine(String symbol) {
        if (matchingEngineMap.containsKey(symbol)) {
            return matchingEngineMap.get(symbol);
        }
        logger.warn("Invalid symbol: " + symbol);
        return null;
    }

    public boolean submitOrder(Order order, boolean broadcast) {
        logger.info("Getting matching engine for " + order.getSymbol());
        MatchingEngine matchingEngine = getMatchingEngine(order.getSymbol());
        logger.info("Got matching engine for " + order.getSymbol());
        try {
            Set<JSONObject> updates = matchingEngine.processOrder(order);
            if (broadcast && updates.size() > 0) {
                broadcastUpdates(updates);
            }
        } catch (Exception e) {
            logger.warn("Could not process order " + order.toString() + ": " + e.getMessage()
                    + ", stack trace: " + Arrays.toString(e.getStackTrace()));
            return false;
        }
        return true;
    }

    // Broadcast new order to websocket connections
    public void broadcastUpdates(Set<JSONObject> updates) {
        logger.info("Broadcasting updates: " + updates.toString());
        if (connections.size() > 0) {
            for (WebSocket connection : connections) {
                for (JSONObject update : updates) {
                    connection.send(update.toString());
                    logger.info("Broadcasting update: " + update.toString());
                }
            }
        } else {
            logger.info("No connections found!");
        }
    }

}
