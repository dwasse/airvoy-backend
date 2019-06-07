package com.airvoy.trading;

import com.airvoy.DatabaseManager;
import com.airvoy.model.*;
import com.airvoy.model.utils.LoggerFactory;
import org.json.simple.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MatchingEngine {

    private final static LoggerFactory logger = new LoggerFactory("MatchingEngine");

    private final Market market;
    private final DatabaseManager databaseManager;
    private final Orderbook orderbook;
    private final Orderbook.QueuePriority queuePriority = Orderbook.QueuePriority.FIFO;
    private Map<String, MatchingEngine> matchingEngineMap;

    public static double MIN_TRADE_AMOUNT = .001;

    public MatchingEngine(Market market, DatabaseManager databaseManager) {
        this.market = market;
        this.databaseManager = databaseManager;
        this.orderbook = new Orderbook(market, queuePriority);
    }

    public void setMatchingEnginePointers(Map<String, MatchingEngine> matchingEngineMap) {
        this.matchingEngineMap = matchingEngineMap;
    }

    public void preprocessSyntheticMargin(Order order) {
        for (String id : order.getAccount().getSyntheticMarginOrders()) {
            Order syntheticOrder = Order.fromId(databaseManager, id);
            try {
                matchingEngineMap.get(syntheticOrder.getSymbol()).cancelOrder(syntheticOrder);
            } catch (Exception e) {
                logger.warn("Error cancelling synthetic margin order: " + syntheticOrder.toString());
            }
        }
    }

    public Set<JSONObject> processOrder(Order order) throws Exception {
        Set<JSONObject> updates = new HashSet<>();
        if (order.getType().equals(Order.Type.LIMIT)) {
            updates.addAll(processLimitOrder(order));
        } else if (order.getType().equals(Order.Type.MARKET)) {
            updates.addAll(processMarketOrder(order));
        } else if (order.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
            updates.addAll(processSyntheticMarginOrder(order));
        } else {
            throw new Exception("Invalid order type: " + order.getType());
        }
        return updates;
    }

    public void fillLevel(Order order, Set<JSONObject> updates, Level level) throws Exception {
        logger.info("Filling order " + order.toString() + " with level " + level.getPrice());
        Order makerOrder = level.getFirstOrder();
        if (makerOrder == null) {
            logger.warn("Maker order is null for level " + level.getPrice());
            return;
        }
        logger.info("Maker order string type: " + makerOrder.getTypeString());
        logger.info("Maker order type: " + makerOrder.getType());
        if (makerOrder.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
            preprocessSyntheticMargin(makerOrder);
        }
        logger.info("Filling maker order: " + makerOrder.toString());
        if (Math.abs(order.getAmount()) >= Math.abs(makerOrder.getAmount())) {
            level.removeOrder(makerOrder.getId());
            logger.info("Fully removed maker order " + makerOrder.getId() + " from level " + level.getPrice());
            updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                    makerOrder.getAmount(), makerOrder, order)));
            makerOrder.fill(makerOrder.getAmount());
            order.fill(makerOrder.getAmount());
            updates.add(getOrderUpdateJson(makerOrder));
            return;
        }
        logger.info("Partially filled maker order " + makerOrder.getId() + " from level " + level.getPrice());
        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                order.getAmount(), makerOrder, order)));
        makerOrder.fill(order.getAmount());
        logger.info("Order amount before fill: " + order.getAmount());
        order.fill(order.getAmount());
        logger.info("Order amount after fill: " + order.getAmount());
        logger.info("Maker order amount after fill: " + makerOrder.getAmount());
        updates.add(getOrderUpdateJson(makerOrder));
    }

    public Set<JSONObject> processLimitOrder(Order order) {
        logger.info("Processing FIFO limit order: " + order.toString());
        Set<JSONObject> updates = new HashSet<>();
        if (order.getSide() == Order.BUY) {
            logger.info("Processing buy limit order " + order.getId() + ", best ask: " + orderbook.getBestAsk());
            // Match orders
            try {
                while (order.getPrice() >= orderbook.getBestAsk() && order.getAmount() > MIN_TRADE_AMOUNT) {
                    logger.info("Order price " + order.getPrice() + " to match against best ask price " + orderbook.getBestAsk());
                    Level currentLevel = orderbook.getLevel(orderbook.getBestAsk());
                    logger.info("Order amount before fillLevel:" + order.getAmount());
                    fillLevel(order, updates, currentLevel);
                    logger.info("Order amount after fillLevel:" + order.getAmount());
                    Order firstOrder = currentLevel.getFirstOrder();
                    if (firstOrder != null) {
                        logger.info("Best order amount after fillLevel: " + currentLevel.getFirstOrder().getAmount());
                    } else {
                        logger.info("First order is null");
                    }

                }
            } catch (Exception e) {
                logger.warn("Exception while filling order " + order.toString() + ": " + e.getMessage()
                        + ", stack trace: " + Arrays.toString(e.getStackTrace()));
            }
            // Add remainder to book
            if (order.getAmount() > MIN_TRADE_AMOUNT) {
                logger.info("Adding remainder " + order.getAmount());
                try {
                    orderbook.addOrder(order);
                    updates.add(getOrderUpdateJson(order));
                } catch (Exception e) {
                    logger.warn("Exception adding order " + order.toString() + " to book: " + e.getMessage()
                            + ", stack trace: " + Arrays.toString(e.getStackTrace()));
                }
            }
        } else if (order.getSide() == Order.SELL) {
            logger.info("Processing sell limit order " + order.getId() + ", best bid: " + orderbook.getBestBid());
            // Match orders
            try {
                while (order.getPrice() <= orderbook.getBestBid() && order.getAmount() > MIN_TRADE_AMOUNT) {
                    logger.info("Order price " + order.getPrice() + " to match against best bid price " + orderbook.getBestBid());
                    Level currentLevel = orderbook.getLevel(orderbook.getBestBid());
                    fillLevel(order, updates, currentLevel);
                }
            } catch (Exception e) {
                logger.warn("Exception while filling order " + order.toString() + ": " + e.getMessage()
                        + ", stack trace: " + Arrays.toString(e.getStackTrace()));
            }
            // Add remainder to book
            if (Math.abs(order.getAmount()) > MIN_TRADE_AMOUNT) {
                try {
                    orderbook.addOrder(order);
                    updates.add(getOrderUpdateJson(order));
                } catch (Exception e) {
                    logger.warn("Exception adding order " + order.toString() + " to book: " + e.getMessage()
                            + ", stack trace: " + Arrays.toString(e.getStackTrace()));
                }
            }
        }
        return updates;
    }

    public Set<JSONObject> processLimitProRata(Order order) {
        logger.info("Processing limit order: " + order.toString());
        Set<JSONObject> updates = new HashSet<>();
        if (order.getSide() == Order.BUY) {
            logger.info("Processing buy limit order " + order.getId() + ", best ask: " + orderbook.getBestAsk());
            // Match orders
            while (order.getPrice() >= orderbook.getBestAsk() && order.getAmount() > 0) {
                logger.info("Order price " + order.getPrice() + " to match against best ask price " + orderbook.getBestAsk());
                Level currentLevel = orderbook.getLevel(orderbook.getBestAsk());
                double totalAmount = currentLevel.getTotalAmount();
                logger.info("Order amount: " + order.getAmount() + ", total amount: " + totalAmount);
                if (order.getAmount() >= totalAmount) {
                    // Match with entire level
                    for (Order makerOrder : currentLevel.getOrders()) {
                        if (makerOrder.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
                            preprocessSyntheticMargin(makerOrder);
                        }
                        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order)));
                        makerOrder.fill(makerOrder.getAmount());
                        order.fill(totalAmount);
                        updates.add(getOrderUpdateJson(makerOrder));
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    logger.info("Processing pro-rata matching");
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
                        if (makerOrder.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
                            preprocessSyntheticMargin(makerOrder);
                        }
                        logger.info("Matching against maker order " + makerOrder.toString());
                        double amount = order.getAmount() * (makerOrder.getAmount() / totalAmount);
                        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order)));
                        makerOrder.fill(amount);
                        order.fill(amount);
                        updates.add(getOrderUpdateJson(makerOrder));
                    }
                }
            }
            // Add remainder to book
            if (order.getAmount() > 0) {
                logger.info("Adding remainder " + order.getAmount());
                try {
                    orderbook.addOrder(order);
                } catch (Exception e) {
                    logger.warn("Exception adding order to book: " + e.getMessage());
                }

            }
        } else if (order.getSide() == Order.SELL) {
            logger.info("Processing sell limit order " + order.getId() + ", best ask: " + orderbook.getBestAsk());
            // Match orders
            while (order.getPrice() <= orderbook.getBestBid() && order.getAmount() > 0) {
                logger.info("Order price " + order.getPrice() + " to match against best ask price " + orderbook.getBestAsk());
                Level currentLevel = orderbook.getLevel(orderbook.getBestBid());
                double totalAmount = currentLevel.getTotalAmount();
                logger.info("Order amount: " + order.getAmount() + ", total amount: " + totalAmount);
                if (order.getAmount() >= totalAmount) {
                    // Match with entire level
                    for (Order makerOrder : currentLevel.getOrders()) {
                        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order)));
                        makerOrder.fill(makerOrder.getAmount());
                        order.fill(totalAmount);
                        updates.add(getOrderUpdateJson(makerOrder));
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    logger.info("Processing pro-rata matching");
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
                        if (makerOrder.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
                            preprocessSyntheticMargin(makerOrder);
                        }
                        logger.info("Matching against maker order " + makerOrder.toString());
                        double amount = order.getAmount() * (makerOrder.getAmount() / totalAmount);
                        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order)));
                        makerOrder.fill(amount);
                        order.fill(amount);
                        updates.add(getOrderUpdateJson(makerOrder));
                    }
                }
            }
            // Add remainder to book
            if (order.getAmount() > 0) {
                try {
                    orderbook.addOrder(order);
                    updates.add(getOrderUpdateJson(order));
                } catch (Exception e) {
                    logger.warn("Exception adding order to book: " + e.getMessage());
                }
            }
        }
        return updates;
    }

    private Set<JSONObject> processMarketOrder(Order order) {
        Set<JSONObject> updates = new HashSet<>();
        if (order.getSide() == Order.BUY) {
            // Match orders
            while (order.getAmount() > 0) {
                Level currentLevel = orderbook.getLevel(orderbook.getBestAsk());
                double totalAmount = currentLevel.getTotalAmount();
                if (totalAmount == 0) {
                    break;
                }
                if (order.getAmount() >= totalAmount) {
                    // Match with entire level
                    for (Order makerOrder : currentLevel.getOrders()) {
                        if (makerOrder.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
                            preprocessSyntheticMargin(makerOrder);
                        }
                        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order)));
                        makerOrder.fill(makerOrder.getAmount());
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
                        if (makerOrder.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
                            preprocessSyntheticMargin(makerOrder);
                        }
                        double amount = order.getAmount() / totalAmount;
                        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order)));
                        makerOrder.fill(amount);
                    }
                }
            }
        } else if (order.getSide() == Order.SELL) {
            // Match orders
            while (order.getAmount() > 0) {
                Level currentLevel = orderbook.getLevel(orderbook.getBestBid());
                double totalAmount = currentLevel.getTotalAmount();
                if (totalAmount == 0) {
                    break;
                }
                if (order.getAmount() >= totalAmount) {
                    // Match with entire level
                    for (Order makerOrder : currentLevel.getOrders()) {
                        if (makerOrder.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
                            preprocessSyntheticMargin(makerOrder);
                        }
                        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order)));
                        makerOrder.fill(makerOrder.getAmount());
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
                        if (makerOrder.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
                            preprocessSyntheticMargin(makerOrder);
                        }
                        double amount = order.getAmount() / totalAmount;
                        updates.addAll(processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order)));
                        makerOrder.fill(amount);
                    }
                }
            }
        }
        return updates;
    }

    private JSONObject getOrderUpdateJson(Order order) {
        JSONObject orderJson = new JSONObject();
        orderJson.put("messageType", "orderUpdate");
        orderJson.put("content", order.toString());
        return orderJson;
    }

    private JSONObject getNewTradeJson(Trade trade) {
        JSONObject orderJson = new JSONObject();
        orderJson.put("messageType", "newTrade");
        orderJson.put("content", trade.toString());
        return orderJson;
    }

    private Set<JSONObject>  processSyntheticMarginOrder(Order order) {
        Set<JSONObject> updates = new HashSet<>();
        //TODO: implement
        return updates;
    }

    private Set<JSONObject> cancelOrder(Order order) throws Exception {
        Set<JSONObject> updates = new HashSet<>();
        orderbook.removeOrder(order.getId(), order.getPrice());
        order.setAmount(0);
        updates.add(getOrderUpdateJson(order));
        return updates;
    }

    public Set<JSONObject> processTrade(Trade trade) {
        Set<JSONObject> updates = new HashSet<>();
        logger.info("Processing trade: " + trade.toString());
        Account makerAccount = trade.getMakerAccount();
        Account takerAccount = trade.getTakerAccount();
        updateAccount(makerAccount, trade.getAmount(), trade.getPrice(), -trade.getSide());
        updateAccount(takerAccount, trade.getAmount(), trade.getPrice(), trade.getSide());
        updates.add(getNewTradeJson(trade));
        return updates;
    }

    public void updateAccount(Account account, double amount, double price, int side) {
        double total = price * amount;
        double position = account.getPosition(market);
        if (side == Order.BUY) {
            if (position >= 0) {
                account.updatePosition(market, amount);
                account.updateBalance(-total);
            } else {
                double filledAmount = 0;
                //Close out entire position
                account.updatePosition(market, -position);
                account.updateBalance(-position * price);
                filledAmount += Math.abs(position);
                if (filledAmount < amount) {
                    // Flip long
                    account.updatePosition(market, amount - filledAmount);
                    account.updateBalance(-(amount - filledAmount) * price);
                }
            }
        } else if (side == Order.SELL) {
            if (position <= 0) {
                account.updatePosition(market, -amount);
                account.updateBalance(-total);
            } else {
                double filledAmount = 0;
                // Close out entire position
                account.updatePosition(market, -position);
                account.updateBalance(-position * price);
                filledAmount += -position;
                if (filledAmount < amount) {
                    // Flip short
                    account.updatePosition(market, -(amount - filledAmount));
                    account.updateBalance(-(amount - filledAmount) * price);
                }
            }
        }
    }

}
