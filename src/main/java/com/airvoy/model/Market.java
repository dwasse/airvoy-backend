package com.airvoy.model;

import com.airvoy.DatabaseManager;
import com.airvoy.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class Market {

    private final static Logger logger = LogManager.getLogger(Market.class);

    public enum Outcomes {
        LONG, SHORT;
    }

    private final String id;
    private String name;
    private long expiry;
    private String symbol;
    private String description;
    private final int tickSize = 5; // Basis points
    private final double makerFee = -.005;
    private final double takerFee = .01;

    // Just binary markets for now

    public Market(String name, String symbol, long expiry) {
        this.name = name;
        this.symbol = symbol;
        this.expiry = expiry;
        this.id = UUID.randomUUID().toString();
    }

    public Market(String symbol, DatabaseManager databaseManager) {
        this.symbol = symbol;
        this.id = UUID.randomUUID().toString();
        ResultSet resultSet = databaseManager.executeQuery("SELECT Name, Expiry FROM Markets WHERE Symbol= \"" + symbol + "\"");
        try {
            resultSet.next();
            this.name = resultSet.getString("Name");
            this.expiry = resultSet.getLong("Expiry");
        } catch (SQLException e) {
            logger.warn("Exception querying for market info: " + e.getMessage());
        }
    }

    public String getSymbol() {
        return symbol;
    }

    public String getId() {
        return id;
    }

    public int getTickSize() {
        return tickSize;
    }

    public double getMakerFee() {
        return makerFee;
    }

    public double getTakerFee() {
        return takerFee;
    }

}
