package com.airvoy.model;

import org.json.simple.JSONObject;

import java.util.UUID;

public class Trade {

    private final Market market;
    private final int side;
    private final Order makerOrder;
    private final Order takerOrder;
    private double price;
    private double amount;
    private long timestamp;
    private double fee;
    private String id;

    public Trade(Market market, int side, double price, double amount, Order makerOrder, Order takerOrder) {
        this.market = market;
        this.side = side;
        this.price = price;
        this.amount = amount;
        this.makerOrder = makerOrder;
        this.takerOrder = takerOrder;
        this.timestamp = System.currentTimeMillis();
        this.id = UUID.randomUUID().toString();
        this.fee = 0; // TODO: implement
    }

    @Override
    public String toString() {
        JSONObject tradeJson = new JSONObject();
        tradeJson.put("timestamp", getTimestamp());
        tradeJson.put("price", getPrice());
        tradeJson.put("amount", getSide() * getAmount());
        tradeJson.put("id", getId());
        return tradeJson.toString();
    }

    public Account getMakerAccount() {
        return makerOrder.getAccount();
    }
    
    public Account getTakerAccount() {
        return takerOrder.getAccount();
    }

    public int getSide() {
        return side;
    }

    public double getAmount() {
        return amount;
    }

    public double getPrice() {
        return price;
    }

    public double getFee() {
        return fee;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

}
