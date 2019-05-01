package com.airvoy.model;

import java.util.*;

public class Level {

    private Map<String, Order> orders = new HashMap<>();
    private final int price;
    private Integer side;

    public Level(int price) {
        this.price = 0;
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
    }

    public void removeOrder(String id) throws Exception {
        if (orders.containsKey(id)) {
            orders.remove(id);
        } else {
            throw new Exception("Id " + id + " not found in level " + price);
        }
    }



}
