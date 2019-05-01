package com.airvoy.model;

import java.util.*;

public class Orderbook {

    public enum QueuePriority {
        PRO_RATA, FIFO;
    }

    private Market market;
    private SortedMap<Double, Level> levels = new TreeMap<>();
    private QueuePriority queuePriority;
    private double tickSize;
    private double bestBid = 0;
    private double bestAsk = 1;

    //Represent levels as basis points
    public Orderbook(Market market, QueuePriority queuePriority) {
        this.market = market;
        this.queuePriority = queuePriority;
        this.tickSize = market.getTickSize();
        double currentLevel = 0;
        while (currentLevel < 1) {
            currentLevel += tickSize;
            levels.put(currentLevel, new Level(currentLevel));
        }
    }

    public double getBestBid() {
        return bestBid;
    }

    public double getBestAsk() {
        return bestAsk;
    }

    public Level getLevel(double price) {
        return levels.get(price);
    }

    // Only add limit orders; do not match here
    public void addOrder(Order order) throws Exception {
        double price = order.getPrice();
        if (!levels.containsKey(price)) {
            throw new Exception("Price " + price + " is not a valid level [tickSize = " + tickSize + "]");
        }
        Level level = levels.get(price);
        if (level.getSide() == null || level.getSide() == price) {
            level.addOrder(order);
            if (order.getSide() == Order.BUY && (bestBid == 0 || price > bestBid)) {
                bestBid = price;
            } else if (order.getSide() == Order.SELL && (bestAsk == 1 || price < bestBid)) {
                bestAsk = price;
            }
        } else {
            throw new Exception("Cannot add order to level if sides do not match");
        }
    }

    public void removeOrder(String id, double price) throws Exception {
        if (levels.containsKey(price)) {
            Level level = levels.get(price);
            level.removeOrder(id);
            int side = level.getSide();
            level.setSide(null);
            if (level.getNumOrders() == 0) {
                // Need to reset best bid/ask
                if (side == Order.BUY) {
                    double currentLevel = price;
                    while (currentLevel > 0) {
                        currentLevel -= tickSize;
                        if (levels.get(currentLevel).getNumOrders() > 0) {
                            bestBid = currentLevel;
                            return;
                        }
                        bestBid = 0;
                    }
                } else if (side == Order.SELL) {
                    double currentLevel = price;
                    while (currentLevel < 1) {
                        currentLevel += tickSize;
                        if (levels.get(currentLevel).getNumOrders() > 0) {
                            bestAsk = currentLevel;
                            return;
                        }
                        bestAsk = 1;
                    }
                }
            }
        } else {
            throw new Exception("Price " + price + " is not a valid level");
        }
    }

}
