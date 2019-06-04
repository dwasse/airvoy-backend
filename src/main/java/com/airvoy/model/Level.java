package com.airvoy.model;

import java.util.HashSet;

public interface Level {

    HashSet<Order> getOrders();

    Integer getSide();

    Orderbook.QueuePriority getPriority();

    int getPrice();

    int getNumOrders();

    Order getFirstOrder();

    double getTotalAmount();

    void setSide(Integer newSide);

    void addOrder(Order order) throws Exception;

    void removeOrder(String id) throws Exception;

}
