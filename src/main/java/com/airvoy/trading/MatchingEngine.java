package com.airvoy.trading;

import com.airvoy.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MatchingEngine {

    private final static Logger logger = LogManager.getLogger(MatchingEngine.class);

    private final Market market;
    private final Orderbook orderbook;

    public MatchingEngine(Market market) {
        this.market = market;
        this.orderbook = new Orderbook(market, Orderbook.QueuePriority.PRO_RATA);
    }

    public void processOrder(Order order) throws Exception {
        double price = order.getPrice();
        if (order.getType().equals(Order.Type.LIMIT)) {
            processLimitOrder(order);
        } else if (order.getType().equals(Order.Type.MARKET)) {
            processMarketOrder(order);
        } else if (order.getType().equals(Order.Type.SYNTHETIC_MARGIN)) {
            processSyntheticMarginOrder(order);
        } else {
            throw new Exception("Invalid order type: " + order.getType());
        }
    }

    public void processLimitOrder(Order order) {
        if (order.getSide() == Order.BUY) {
            // Match orders
            while (order.getPrice() >= orderbook.getBestAsk()) {
                Level currentLevel = orderbook.getLevel(orderbook.getBestAsk());
                double totalAmount = currentLevel.getTotalAmount();
                if (order.getAmount() >= totalAmount) {
                    // Match with entire level
                    for (Order makerOrder : currentLevel.getOrders()) {
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order));
                        makerOrder.fill(makerOrder.getAmount());
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
                        double amount = order.getAmount() / totalAmount;
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order));
                        makerOrder.fill(amount);
                    }
                }
            }
            // Add remainder to book
            if (order.getAmount() > 0) {
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
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
                        double amount = order.getAmount() / totalAmount;
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order));
                        makerOrder.fill(amount);
                    }
                }
            }
            // Add remainder to book
            if (order.getAmount() > 0) {
                try {
                    orderbook.addOrder(order);
                } catch (Exception e) {
                    logger.warn("Exception adding order to book: " + e.getMessage());
                }

            }
        }
    }

    private void processMarketOrder(Order order) {
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
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order));
                        makerOrder.fill(makerOrder.getAmount());
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
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
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(),
                                makerOrder.getAmount(), makerOrder, order));
                        makerOrder.fill(makerOrder.getAmount());
                    }
                    order.setAmount(order.getAmount() - totalAmount);
                } else {
                    // Pro-rata matching
                    for (Order makerOrder : currentLevel.getOrders()) {
                        double amount = order.getAmount() / totalAmount;
                        processTrade(new Trade(market, order.getSide(), makerOrder.getPrice(), amount, makerOrder,
                                order));
                        makerOrder.fill(amount);
                    }
                }
            }
        }
    }

    private void  processSyntheticMarginOrder(Order order) {
        //TODO: implement
    }

    public void processTrade(Trade trade) {
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
