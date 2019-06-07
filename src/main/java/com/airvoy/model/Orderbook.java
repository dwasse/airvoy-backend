package com.airvoy.model;

import com.airvoy.model.utils.LoggerFactory;

import java.util.*;

public class Orderbook {

    public enum QueuePriority {
        PRO_RATA, FIFO;
    }

    private final static LoggerFactory logger = new LoggerFactory("Orderbook");

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
            if (queuePriority.equals(QueuePriority.PRO_RATA)) {
                levels.put(currentLevel, new LevelProRata(currentLevel));
            } else if (queuePriority.equals(QueuePriority.FIFO)) {
                levels.put(currentLevel, new LevelFIFO(currentLevel));
            }
        }
    }

    public double getBestBid() {
        return bestBid;
    }

    public double getBestAsk() {
        return bestAsk;
    }

    public Level getLevel(double price) {
        return levels.get(getIntPrice(price));
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
                logger.info("Added new best bid: " + bestBid);
            } else if (order.getSide() == Order.SELL && (bestAsk == 1 || price < bestAsk)) {
                bestAsk = price;
                logger.info("Added new best ask: " + bestAsk);
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
            if (level.getNumOrders() == 0) {
                // Need to reset best bid/ask
                if (side == Order.BUY) {
                    double currentLevel = intPrice;
                    while (currentLevel > 0) {
                        currentLevel -= tickSize;
                        if (levels.get(currentLevel).getNumOrders() > 0) {
                            bestBid = currentLevel;
                            logger.info("Updated best bid to " + bestBid);
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
                            logger.info("Updated best ask to " + bestAsk);
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
