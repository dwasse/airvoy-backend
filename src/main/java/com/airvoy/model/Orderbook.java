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
    private TreeSet<Integer> activeBids = new TreeSet<>(new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
            return o2.compareTo(o1);
        }
    });
    private TreeSet<Integer> activeAsks = new TreeSet<>(new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
            return o1.compareTo(o2);
        }
    });
    private QueuePriority queuePriority;
    private int tickSize;
    private int bestBid = 0;
    private int bestAsk = 1000;
    private double doublePrecision = .000001;
    private final int MAX_PRICE = 1000;
    private final int MIN_PRICE = 0;

    //Represent levels as basis points
    public Orderbook(Market market, QueuePriority queuePriority) {
        this.market = market;
        this.queuePriority = queuePriority;
        this.tickSize = market.getTickSize();
        int currentLevel = 0;
        while (currentLevel < MAX_PRICE) {
            currentLevel += tickSize;
            if (queuePriority.equals(QueuePriority.PRO_RATA)) {
                levels.put(currentLevel, new LevelProRata(currentLevel));
            } else if (queuePriority.equals(QueuePriority.FIFO)) {
                levels.put(currentLevel, new LevelFIFO(currentLevel));
            }
        }
        logger.info("Initialized orderbook with levels: " + levels.keySet().toString());
    }

    @Override
    public String toString() {
        String orderString = "";
        int currentPrice = MAX_PRICE;
        while (currentPrice > MIN_PRICE) {
            currentPrice -= tickSize;
            Level currentLevel = levels.get(currentPrice);
            if (currentLevel != null) {
                if (currentLevel.getNumOrders() > 0) {
                    orderString += getDoublePrice(currentPrice) + "  " + (currentLevel.getSide() * currentLevel.getTotalAmount()) + "\n";
                }
            }
        }
        return orderString;
    }

    public int getBestBid() {
        if (activeBids.size() > 0) {
            logger.info("Got best bid: " + activeBids.first());
            return activeBids.first();
        }
        logger.info("Could not find any bids");
        return MIN_PRICE;
    }

    public int getBestAsk() {
        if (activeAsks.size() > 0) {
            logger.info("Got best ask: " + activeAsks.first());
            return activeAsks.first();
        }
        logger.info("Could not find any asks");
        return MAX_PRICE;
    }

//    public int getBestBid() {
//        return bestBid;
//    }
//
//    public int getBestAsk() {
//        return bestAsk;
//    }

    public Level getLevel(double price) {
        return levels.get(getIntPrice(price));
    }

    public Level getLevel(int price) {
        logger.info("Getting level for price " + price);
        return levels.get(price);
    }

    public static int getIntPrice(double price) {
        //TODO: validate to doublePrecision
        return (int) (price * 1000);
    }

    public static double getDoublePrice(int price) {
        return ((double) price) / 1000;
    }

    // Only add limit orders; do not match here
    public void addOrder(Order order) throws Exception {
        double price = order.getPrice();
        int intPrice = getIntPrice(price);
//        order.setPrice(((double) intPrice) / 1000);
        if (!levels.containsKey(intPrice)) {
            throw new Exception("Price " + price + " is not a valid level [tickSize = .00" + tickSize + "]");
        }
        Level level = levels.get(intPrice);
        if (level.getSide() == null || level.getSide() == order.getSide()) {
            logger.info("Adding order " + order.toString() + " to level " + intPrice);
            logger.info("Active asks: " + activeAsks.toString() + ", active bids:"  + activeBids.toString());
            level.addOrder(order);
            if (order.getSide() == Order.BUY && !activeBids.contains(intPrice)) {
                logger.info("Adding price " + intPrice + " to active bids: " + activeBids.toString());
                activeBids.add(intPrice);
            } else if (order.getSide() == Order.SELL && !activeAsks.contains(intPrice)) {
                logger.info("Adding price " + intPrice + " to active asks: " + activeAsks.toString());
                activeAsks.add(intPrice);
            }
            if (order.getSide() == Order.BUY && (bestBid == MIN_PRICE || intPrice > bestBid)) {
                logger.info("Changing best bid from " + bestBid + " to " + intPrice);
                bestBid = intPrice;
                logger.info("Added new best bid: " + bestBid);
            } else if (order.getSide() == Order.SELL && (bestAsk == MAX_PRICE || intPrice < bestAsk)) {
                logger.info("Changing best ask from " + bestAsk + " to " + intPrice);
                bestAsk = intPrice;
                logger.info("Added new best ask: " + bestAsk);
            }
        } else {
            throw new Exception("Cannot add order to level if sides do not match");
        }
    }

    public void removeOrder(String id, double price, int side) throws Exception {
        logger.info("Removing order " + id + " from orderbook with price " + price + " and side " + side);
        int intPrice = getIntPrice(price);
        if (levels.containsKey(intPrice)) {
            Level level = levels.get(intPrice);
//            int side = level.getSide();
            level.removeOrder(id);
            if (level.getNumOrders() == 0) {
                if (side == Order.BUY) {
                    logger.info("Removing price " + intPrice + " from active bids: " + activeBids.toString());
                    activeBids.remove(intPrice);
                } else if (side == Order.SELL) {
                    logger.info("Removing price " + intPrice + " from active asks: " + activeAsks.toString());
                    activeAsks.remove(intPrice);
                }
                logger.info("Active asks: " + activeAsks.toString() + ", active bids:"  + activeBids.toString());

                // Need to reset best bid/ask
                if (side == Order.BUY && level.getPrice() == bestBid) {
                    logger.info("Resetting best bid");
                    int currentPrice = MAX_PRICE - tickSize;
                    Level currentLevel;
                    while (currentPrice > MIN_PRICE) {
                        currentPrice -= tickSize;
                        currentLevel = levels.get(currentPrice);
//                        logger.info("Inspecting price for best bid reset: " + currentPrice);
                        if (currentLevel != null) {
//                            logger.info("Current level: " + currentLevel.toString());
                            if (currentLevel.getSide() != null) {
                                if (currentLevel.getSide() == side && currentLevel.getNumOrders() > 0) {
                                    bestBid = currentPrice;
                                    logger.info("Updated best bid to " + bestBid);
                                    return;
                                }
                            }
                        }
                        bestBid = MIN_PRICE;
                    }
                } else if (side == Order.SELL && level.getPrice() == bestAsk) {
                    logger.info("Resetting best ask");
                    //TODO: dont need to iterate through all prices
                    int currentPrice = MIN_PRICE + tickSize;
                    Level currentLevel;
                    while (currentPrice < MAX_PRICE) {
                        currentPrice += tickSize;
                        currentLevel = levels.get(currentPrice);
//                        logger.info("Inspecting price for best ask reset: " + currentPrice);
                        if (currentLevel != null) {
//                            logger.info("Current level: " + currentLevel.toString());
                            if (currentLevel.getSide() != null) {
                                if (currentLevel.getSide() == side && currentLevel.getNumOrders() > 0) {
                                    bestAsk = currentPrice;
                                    logger.info("Updated best ask to " + bestAsk);
                                    return;
                                }
                            }
                        }
                        bestAsk = MAX_PRICE;
                    }
                }
            }
        } else {
            throw new Exception("Price " + price + " is not a valid level");
        }
    }

}
