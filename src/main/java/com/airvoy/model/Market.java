package com.airvoy.model;

import java.util.UUID;

public class Market {

    public enum Outcomes {
        LONG, SHORT;
    }

    private final String id;
    private final String name;
    private final long expiry;
    private String symbol;
    private String description;
    private final double tickSize = .005;
    private final double makerFee = -.005;
    private final double takerFee = .01;

    // Just binary markets for now

    public Market(String name, String symbol, long expiry) {
        this.name = name;
        this.symbol = symbol;
        this.expiry = expiry;
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public double getTickSize() {
        return tickSize;
    }

    public double getMakerFee() {
        return makerFee;
    }

    public double getTakerFee() {
        return takerFee;
    }

}
