package com.airvoy.model;

import com.airvoy.model.utils.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

public class LevelFIFO implements Level {

    private final static LoggerFactory logger = new LoggerFactory("LevelFIFO");

    private Map<String, Order> orders = new HashMap<>();
    private PriorityQueue<Order> orderQueue = new PriorityQueue<>((a,b) -> (int) (a.getTimestamp() - b.getTimestamp()));
    private final int price;
    private Integer side = null;
    private final Orderbook.QueuePriority queuePriority = Orderbook.QueuePriority.FIFO;

    public LevelFIFO(int price) {
        this.price = price;
    }

    public Order getFirstOrder() {
        logger.info("Getting first order with order queue: " + orderQueue.toString());
        if (orderQueue.size() > 0) {
            return orderQueue.peek();
        }
        return null;
    }

    @Override
    public String toString() {
        return "LevelFIFO with price " + price + ", side " + side;
    }

    public Orderbook.QueuePriority getPriority() {
        return queuePriority;
    }

    public int getPrice() {
        return price;
    }

    public HashSet<Order> getOrders() {
        return new HashSet<>(orders.values());
    }

    public Integer getSide() {
        return side;
    }

    public int getNumOrders() {
        return orders.size();
    }

    public double getTotalAmount() {
        double totalAmount = 0;
        for (Order order : orders.values()) {
            totalAmount += order.getAmount();
        }
        return totalAmount;
    }

    public void setSide(Integer newSide) {
        side = newSide;
    }

    public void addOrder(Order order) throws Exception {
        orders.put(order.getId(), order);
        orderQueue.add(order);
        if (side == null) {
            setSide(order.getSide());
        }
    }

    public void removeOrder(String id) throws Exception {
        logger.info("Removing order " + id + " from level " + price);
        logger.info("Number of orders in queue before remove: " + orderQueue.size());
        if (orders.containsKey(id)) {
            orders.remove(id);
            orderQueue.removeIf(o -> o.getId().equals(id));
        } else {
            throw new Exception("Id " + id + " not found in level " + price);
        }
        logger.info("Number of orders in queue after remove: " + orderQueue.size());
        if (getNumOrders() == 0) {
            setSide(null);
        }
    }

}
