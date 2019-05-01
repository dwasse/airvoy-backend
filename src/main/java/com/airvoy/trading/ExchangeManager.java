package com.airvoy.trading;

import com.airvoy.DatabaseManager;
import com.airvoy.model.Market;
import com.airvoy.model.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ExchangeManager {

    private final static Logger logger = LogManager.getLogger(ExchangeManager.class);

    private DatabaseManager databaseManager;
    private Map<String, MatchingEngine> matchingEngineMap = new HashMap<>();

    public ExchangeManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        generateMatchingEngines();
    }

    private void generateMatchingEngines() {
        ResultSet resultSet = databaseManager.executeQuery("SELECT Name, Symbol, Expiry FROM Markets");
        try {
            while (resultSet.next()) {
                String marketName = resultSet.getString("Name");
                String symbol = resultSet.getString("Symbol");
                long expiry = resultSet.getLong("Expiry");
                matchingEngineMap.put(symbol, new MatchingEngine(new Market(marketName, symbol, expiry)));
            }
        } catch (SQLException e) {
            logger.warn("Error generating JSON response: " + e.getMessage());
        }
    }

    public void generateMatchingEngine(String symbol) {
        if (!matchingEngineMap.containsKey(symbol)) {
            Market market = new Market(symbol, databaseManager);
            matchingEngineMap.put(symbol, new MatchingEngine(market));
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

    public boolean submitOrder(Order order) {
        logger.info("Getting matching engine for " + order.getSymbol());
        MatchingEngine matchingEngine = getMatchingEngine(order.getSymbol());
        logger.info("Got matching engine for " + order.getSymbol());
        try {
            matchingEngine.processOrder(order);
        } catch (Exception e) {
            logger.warn("Could not process order: " + order.toString());
            return false;
        }
        return true;
    }

}