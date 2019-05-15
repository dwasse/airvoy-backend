package com.airvoy;

import com.airvoy.model.Account;
import com.airvoy.model.Market;
import com.airvoy.model.Order;
import com.airvoy.model.utils.LoggerFactory;
import com.airvoy.trading.ExchangeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Server extends WebSocketServer {

    private final static LoggerFactory logger = new LoggerFactory("Server");

    private DatabaseManager databaseManager;
    private ExchangeManager exchangeManager;
    private Set<WebSocket> connections;

    public Server(int port, DatabaseManager databaseManager, ExchangeManager exchangeManager) {
	    super(new InetSocketAddress(port));
	    this.databaseManager = databaseManager;
	    this.exchangeManager = exchangeManager;
        connections = new HashSet<>();
        exchangeManager.setConnections(connections);
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        connections.add(webSocket);

        logger.info("Connection established from: " + webSocket.getRemoteSocketAddress().getHostString());
        logger.info("New connection from " + webSocket.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        logger.info("Connection closed");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.info("Received message " + message + " from " + conn.getRemoteSocketAddress().getHostName());
        JSONParser parser = new JSONParser();
        try {
            JSONObject receivedJson = (JSONObject) parser.parse(message);
            processJson(conn, receivedJson);

        } catch (ParseException e) {
            logger.warn("Exception parsing received message: " + e.getMessage());
        }
    }

    private void processJson(WebSocket conn, JSONObject jsonObject) {
        if (jsonObject.containsKey("command")) {
            logger.info("Command: " + jsonObject.get("command").toString());
            switch (jsonObject.get("command").toString()) {
                case "getMarkets":
                    ResultSet resultSet = databaseManager.executeQuery("SELECT Name, Symbol, Expiry FROM Markets");
                    JSONArray contentArray = new JSONArray();
                    JSONObject responseObject = new JSONObject();
                    responseObject.put("messageType", "getMarkets");
                    responseObject.put("success", "true");
                    try {
                        while (resultSet.next()) {
                            JSONObject contentObject = new JSONObject();
                            String marketName = resultSet.getString("Name");
                            String symbol = resultSet.getString("Symbol");
                            long expiry = resultSet.getLong("Expiry");
                            logger.info("Got market name: " + marketName + ", symbol: " + symbol + ", expiry: " + String.valueOf(expiry));
                            contentObject.put("marketName", marketName);
                            contentObject.put("symbol", symbol);
                            contentObject.put("expiry", expiry);
                            contentArray.add(contentObject);
                        }
                        responseObject.put("content", contentArray.toString());
                        logger.info("Sending response: " + responseObject.toString());
                        conn.send(responseObject.toString());
                    } catch (SQLException e) {
                        logger.warn("Error generating JSON response: " + e.getMessage());
                    }
                    break;
                case "getOrderbook":
                    String symbol = "";
                    if (jsonObject.containsKey("symbol")) {
                        symbol = jsonObject.get("symbol").toString();
                    } else {
                        logger.warn("No symbol specified for getOrderbook command");
                    }
                    logger.info("Processing getOrderbook command with symbol " + symbol);
                    resultSet = databaseManager.executeQuery("SELECT Id, Price, Amount FROM Orderbooks WHERE Symbol= \"" + symbol + "\"");
                    contentArray = new JSONArray();
                    responseObject = new JSONObject();
                    responseObject.put("messageType", "getOrderbook");
                    responseObject.put("success", "true");
                    try {
                        while (resultSet.next()) {
                            JSONObject contentObject = new JSONObject();
                            String id = resultSet.getString("Id");
                            double price = resultSet.getDouble("Price");
                            double amount = resultSet.getDouble("Amount");
                            logger.info("Got id: " + id + ", price: " + price + ", amount: " + amount);
                            contentObject.put("id", id);
                            contentObject.put("price", price);
                            contentObject.put("amount", amount);
                            contentArray.add(contentObject);
                        }
                        JSONObject contentObject = new JSONObject();
                        contentObject.put("orders", contentArray.toString());
                        contentObject.put("symbol", symbol);
                        responseObject.put("content", contentObject);
                        logger.info("Sending response: " + responseObject.toString());
                        conn.send(responseObject.toString());
                    } catch (Exception e) {
                        logger.warn("Error processing message: " + e.getMessage());
                    }
                    break;
                    //TODO: account validation
                case "submitOrder":
                    symbol = "";
                    double price = 0;
                    double amount = 0;
                    String sideString = "";
                    int side = 0;
                    String username = "";
                    String type = "";
                    if (jsonObject.containsKey("order")) {
                        JSONParser parser = new JSONParser();
                        JSONObject orderObject;
                        try {
                            orderObject = (JSONObject) parser.parse(jsonObject.get("order").toString());
                            logger.info("Order object: " + orderObject.toString());
                            if (orderObject.containsKey("symbol")
                                    && orderObject.containsKey("price")
                                    && orderObject.containsKey("amount")
                                    && orderObject.containsKey("type")
                                    && orderObject.containsKey("username")
                                    && orderObject.containsKey("side")) {
                                symbol = orderObject.get("symbol").toString();
                                price = (Double) orderObject.get("price");
                                amount = (Double) orderObject.get("amount");
                                sideString = orderObject.get("side").toString();
                                if (sideString.equals("buy")) {
                                    side = 1;
                                } else if (sideString.equals("sell")) {
                                    side = -1;
                                } else {
                                    logger.warn("Invalid side for order: " + sideString);
                                    break;
                                }
                                username = orderObject.get("username").toString();
                                type = orderObject.get("type").toString();
                                logger.info("Processing submitOrder with symbol " + symbol + ", price " + String.valueOf(price)
                                        + ", amount " + String.valueOf(amount) + ", type " + type + ", side " + side);
                                Market market = Market.fromSymbol(databaseManager, symbol);
                                Account account = new Account(username);
                                Order order = new Order(market, side, price, amount, account, Order.getOrderType(type));
                                exchangeManager.submitOrder(order, true);
                            } else {
                                logger.warn("Malformed order submission: " + orderObject.toString());
                            }
                        } catch (ParseException e) {
                            logger.warn("Error parsing order object: " + jsonObject.toString());
                        }
                    } else {
                        logger.warn("Malformed order submission: " + jsonObject.toString());
                    }
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            connections.remove(conn);
        }
        assert conn != null;
        logger.warn("Error detected: " + ex.getMessage() + ", stack trace: " + Arrays.toString(ex.getStackTrace()));
    }

    private void broadcastMessage(String msg) {
        String messageJson = msg;
        for (WebSocket sock : connections) {
            sock.send(messageJson);
        }
    }

}
