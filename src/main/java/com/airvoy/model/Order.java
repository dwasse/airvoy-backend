package com.airvoy.model;

import com.airvoy.DatabaseManager;
import com.airvoy.model.utils.LoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class Order {

    private final static LoggerFactory logger = new LoggerFactory("Order");

    public enum Type {
        LIMIT, MARKET, SYNTHETIC_MARGIN
    }

    public static Integer BUY = 1;
    public static Integer SELL = -1;

    private final Market market;
    private final Integer side;
    private final Account account;
    private double price;
    private double amount;
    private double filledAmount = 0;
    private Type type;
    private String id;
    private long timestamp;
    private boolean filled = false;

    public Order(Market market, int side, double price, double amount, Account account, Type type) {
        this.market = market;
        this.side = side;
        this.price = price;
        this.amount = amount;
        this.account = account;
        this.type = type;
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public Order(String id, Market market, int side, double price, double amount, Account account, Type type) {
        this.market = market;
        this.side = side;
        this.price = price;
        this.amount = amount;
        this.account = account;
        this.type = type;
        this.id = id;
        this.timestamp = System.currentTimeMillis();
    }

    public static Order fromId(DatabaseManager databaseManager, String id) {
        ResultSet resultSet = databaseManager.executeQuery("SELECT Symbol, Price, Amount, Username, Type FROM Orderbooks WHERE Id= \"" + id + "\"");
        try {
            resultSet.next();
            Market market = Market.fromSymbol(databaseManager, resultSet.getString("Symbol"));
            double price = resultSet.getDouble("Price");
            double amount = resultSet.getDouble("Amount");
            Account account = new Account(resultSet.getString("Username"));
            Type type = getOrderType(resultSet.getString("Type"));
            return new Order(id, market, (int) Math.signum(amount), price, Math.abs(amount), account, type);
        } catch (SQLException e) {
            logger.warn("Exception querying for market info: " + e.getMessage());
        }
        return null;
    }

    public static Type getOrderType(String typeString) {
        if (typeString.equals("limit")) {
            return Type.LIMIT;
        }
        if (typeString.equals("market")) {
            return Type.MARKET;
        }
        if (typeString.equals("syntheticMargin")) {
            return Type.SYNTHETIC_MARGIN;
        }
        return null;
    }

    @Override
    public String toString() {
        JSONObject orderJson = new JSONObject();
        orderJson.put("symbol", getSymbol());
        orderJson.put("price", getPrice());
        orderJson.put("amount", getSide() * getAmount());
        orderJson.put("type", getTypeString());
        orderJson.put("timestamp", getTimestamp());
        orderJson.put("id", getId());
        return orderJson.toString();
    }

    public Market getMarket() {
        return market;
    }

    public String getSymbol() {
        return market.getSymbol();
    }

    public int getSide() {
        return side;
    }

    public double getPrice() {
        return price;
    }

    public double getAmount() {
        return amount;
    }

    public double getFilledAmount() {
        return filledAmount;
    }

    public Account getAccount() {
        return account;
    }

    public Type getType() {
        return type;
    }

    public String getTypeString() {
        switch (getType()) {
            case LIMIT:
                return "limit";
            case MARKET:
                return "market";
            case SYNTHETIC_MARGIN:
                return "syntheticMargin";
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setAmount(double newAmount) {
        amount = newAmount;
    }

    public void fill(double fillAmount) {
        filledAmount += fillAmount;
        amount -= fillAmount;
        if (amount == 0) {
            setFilled(true);
        }
    }

    public void setFilled(boolean isFilled) {
        filled = isFilled;
    }

    public void setPrice(double newPrice) {
        logger.info("Setting price " + price + " to new price " + newPrice);
        price = newPrice;
    }

}
