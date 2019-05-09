package com.airvoy.trading;

import com.airvoy.DatabaseManager;
import com.airvoy.model.*;
import com.airvoy.model.utils.LoggerFactory;
import org.json.simple.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MatchingEngine {

    private final static LoggerFactory logger = new LoggerFactory("MatchingEngine");

    private final Market market;
    private final DatabaseManager databaseManager;
    private final Orderbook orderbook;
    private Map<String, MatchingEngine> matchingEngineMap;

    public MatchingEngine(Market market, DatabaseManager databaseManager) {
        this.market = market;
        this.databaseManager = databaseManager;
        this.orderbook = new Orderbook(market, Orderbook.QueuePriority.PRO_RATA);
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

    public Set<JSONObject> processLimitOrder(Order order) {
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
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order));
                        makerOrder.fill(makerOrder.getAmount());
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
                        double amount = order.getAmount() / totalAmount;
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order));
                        makerOrder.fill(amount);
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
            // Match orders
            while (order.getPrice() <= orderbook.getBestBid()) {
                Level currentLevel = orderbook.getLevel(orderbook.getBestBid());
                double totalAmount = currentLevel.getTotalAmount();
                if (order.getAmount() >= totalAmount) {
                    // Match with entire level
                    for (Order makerOrder : currentLevel.getOrders()) {
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order));
                        makerOrder.fill(makerOrder.getAmount());
                        updates.add(getOrderUpdateJson(makerOrder));
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
                        double amount = order.getAmount() / totalAmount;
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order));
                        makerOrder.fill(amount);
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
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order));
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
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order));
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
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order));
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
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order));
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

    public void processTrade(Trade trade) {
        logger.info("Processing trade: " + trade.toString());
        Account makerAccount = trade.getMakerAccount();
        Account takerAccount = trade.getTakerAccount();
        updateAccount(makerAccount, trade.getAmount(), trade.getPrice(), -trade.getSide());
        updateAccount(takerAccount, trade.getAmount(), trade.getPrice(), trade.getSide());
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
