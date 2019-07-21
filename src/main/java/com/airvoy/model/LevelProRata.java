package com.airvoy.model;

import java.util.*;

public class LevelProRata implements Level {

    private Map<String, Order> orders = new HashMap<>();
    private final int price;
    private Integer side;
    private final Orderbook.QueuePriority queuePriority = Orderbook.QueuePriority.PRO_RATA;

    public LevelProRata(int price) {
        this.price = price;
    }

    public HashSet<Order> getOrders() {
        return new HashSet<>(orders.values());
    }

    public int getPrice() {
        return price;
    }

    public Order getFirstOrder() {
        return null;
    }

    public Orderbook.QueuePriority getPriority() {
        return queuePriority;
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
    }

    public void removeOrder(String id) throws Exception {
        if (orders.containsKey(id)) {
            orders.remove(id);
        } else {
            throw new Exception("Id " + id + " not found in level " + price);
        }
    }

}
