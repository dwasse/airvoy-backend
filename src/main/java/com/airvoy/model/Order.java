package com.airvoy.model;

import java.util.UUID;

public class Order {

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

    public Market getMarket() {
        return market;
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

}
