package com.airvoy.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

public class LevelFIFO implements Level {

    private Map<String, Order> orders = new HashMap<>();
    private PriorityQueue<Order> orderQueue = new PriorityQueue<>((a,b) -> (int) (a.getTimestamp() - b.getTimestamp()));
    private final int price;
    private Integer side;
    private final Orderbook.QueuePriority queuePriority = Orderbook.QueuePriority.FIFO;

    public LevelFIFO(int price) {
        this.price = price;
    }

    public Order getFirstOrder() {
        if (orderQueue.size() > 0) {
            return orderQueue.peek();
        }
        return null;
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
    }

    public void removeOrder(String id) throws Exception {
        if (orders.containsKey(id)) {
            orders.remove(id);
            orderQueue.removeIf(o -> o.getId().equals(id));
        } else {
            throw new Exception("Id " + id + " not found in level " + price);
        }
    }

}
