package com.airvoy.model;

import java.util.HashMap;
import java.util.Map;

public class Account {

    private String username;
    private double balance = 0;
    private Map<String, Double> positions = new HashMap<>();

    public Account(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public double getBalance() {
        return balance;
    }

    public double getPosition(Market market) {
        return positions.getOrDefault(market.getId(), (double) 0);
    }

    public void updatePosition(Market market, double positionChange) {
        positions.put(market.getId(), positionChange);
    }

    public void updateBalance(double balanceChange) {
        balance = balanceChange;
    }

}
