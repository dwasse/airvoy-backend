package com.airvoy.model;

import java.util.UUID;

public class Trade {

    private final Market market;
    private final int side;
    private final Order makerOrder;
    private final Order takerOrder;
    private double price;
    private double amount;
    private String id;

    public Trade(Market market, int side, double price, double amount, Order makerOrder, Order takerOrder) {
        this.market = market;
        this.side = side;
        this.price = price;
        this.amount = amount;
        this.makerOrder = makerOrder;
        this.takerOrder = takerOrder;
        this.id = UUID.randomUUID().toString();
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

}
