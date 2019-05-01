package com.airvoy;

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

    private final static Logger logger = LogManager.getLogger(Server.class);

    private DatabaseManager databaseManager;
    private Set<WebSocket> conns;

    public Server(int port, DatabaseManager databaseManager) {
	    super(new InetSocketAddress(port));
	    this.databaseManager = databaseManager;
        conns = new HashSet<>();
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        conns.add(webSocket);

        logger.info("Connection established from: " + webSocket.getRemoteSocketAddress().getHostString());
        System.out.println("New connection from " + webSocket.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        conns.remove(conn);

        logger.info("Connection closed to: " + conn.getRemoteSocketAddress().getHostString());
        System.out.println("Closed connection to " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Received message " + message + " from " + conn.getRemoteSocketAddress().getHostName());
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
            System.out.println("Command: " + jsonObject.get("command").toString());
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
                            System.out.println("Got market name: " + marketName + ", symbol: " + symbol + ", expiry: " + String.valueOf(expiry));
                            contentObject.put("marketName", marketName);
                            contentObject.put("symbol", symbol);
                            contentObject.put("expiry", expiry);
                            contentArray.add(contentObject);
                        }
                        responseObject.put("content", contentArray.toString());
                        System.out.println("Sending response: " + responseObject.toString());
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
                    System.out.println("Processing getOrderbook command with symbol " + symbol);
                    resultSet = databaseManager.executeQuery("SELECT Price, Amount FROM Orders WHERE Symbol= \"" + symbol + "\"");
                    contentArray = new JSONArray();
                    responseObject = new JSONObject();
                    responseObject.put("messageType", "getOrderbook");
                    responseObject.put("success", "true");
                    try {
                        while (resultSet.next()) {
                            JSONObject contentObject = new JSONObject();
                            double price = resultSet.getDouble("Price");
                            double amount = resultSet.getDouble("Amount");
                            System.out.println("Got price: " + price + ", amount: " + amount);
                            contentObject.put("price", price);
                            contentObject.put("amount", amount);
                            contentArray.add(contentObject);
                        }
                        JSONObject contentObject = new JSONObject();
                        contentObject.put("orders", contentArray.toString());
                        contentObject.put("symbol", symbol);
                        responseObject.put("content", contentObject);
                        System.out.println("Sending response: " + responseObject.toString());
                        conn.send(responseObject.toString());
                    } catch (Exception e) {
                        logger.warn("Error processing message: " + e.getMessage());
                    }
                    break;
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            conns.remove(conn);
        }
        assert conn != null;
        logger.warn("Error detected: " + ex.getMessage() + ", stack trace: " + Arrays.toString(ex.getStackTrace()));
    }

    private void broadcastMessage(String msg) {
        String messageJson = msg;
        for (WebSocket sock : conns) {
            sock.send(messageJson);
        }
    }

}
