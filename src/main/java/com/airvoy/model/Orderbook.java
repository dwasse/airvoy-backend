package com.airvoy.model;

import java.util.*;

public class Orderbook {

    public enum QueuePriority {
        PRO_RATA, FIFO;
    }

    private Market market;
    private SortedMap<Integer, Level> levels = new TreeMap<>();
    private QueuePriority queuePriority;
    private int tickSize;
    private double bestBid = 0;
    private double bestAsk = 1;
    private double doublePrecision = .000001;

    //Represent levels as basis points
    public Orderbook(Market market, QueuePriority queuePriority) {
        this.market = market;
        this.queuePriority = queuePriority;
        this.tickSize = market.getTickSize();
        int currentLevel = 0;
        while (currentLevel < 1000) {
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

    private int getIntPrice(double price) {
        //TODO: validate to doublePrecision
        return (int) (price * 1000);
    }

    // Only add limit orders; do not match here
    public void addOrder(Order order) throws Exception {
        double price = order.getPrice();
        int intPrice = getIntPrice(price);
        order.setPrice(((double) intPrice) / 1000);
        if (!levels.containsKey(intPrice)) {
            throw new Exception("Price " + price + " is not a valid level [tickSize = .00" + tickSize + "]");
        }
        Level level = levels.get(intPrice);
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
        int intPrice = getIntPrice(price);
        if (levels.containsKey(intPrice)) {
            Level level = levels.get(intPrice);
            level.removeOrder(id);
            int side = level.getSide();
            level.setSide(null);
            if (level.getNumOrders() == 0) {
                // Need to reset best bid/ask
                if (side == Order.BUY) {
                    double currentLevel = intPrice;
                    while (currentLevel > 0) {
                        currentLevel -= tickSize;
                        if (levels.get(currentLevel).getNumOrders() > 0) {
                            bestBid = currentLevel;
                            return;
                        }
                        bestBid = 0;
                    }
                } else if (side == Order.SELL) {
                    double currentLevel = intPrice;
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
