package com.airvoy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

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
        if (message.substring(0, 9).equals("getMarket")) {
            try {
                ResultSet resultSet = databaseManager.executeQuery("SELECT Price, Amount FROM Orders");
                JSONArray jsonArray = new JSONArray();
                while (resultSet.next()) {
                    JSONObject jsonObject = new JSONObject();
                    double price = resultSet.getDouble("Price");
                    double amount = resultSet.getDouble("Amount");
                    System.out.println("Got price: " + price + ", amount: " + amount);
                    jsonObject.put("Type", "Order");
                    jsonObject.put("Price", price);
                    jsonObject.put("Amount", amount);
                    jsonArray.add(jsonObject);
                }
                conn.send(jsonArray.toString());
            } catch (Exception e) {
                logger.warn("Error processing message: " + e.getMessage());
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
