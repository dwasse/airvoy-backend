package com.airvoy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

public class Server extends WebSocketServer {

    private final static Logger logger = LogManager.getLogger(Server.class);
    private Set<WebSocket> conns;

    private Server(int port) {
	    super(new InetSocketAddress(port));
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
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            conns.remove(conn);
        }
        assert conn != null;
        System.out.println("ERROR from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    private void broadcastMessage(String msg) {
        String messageJson = msg;
        for (WebSocket sock : conns) {
            sock.send(messageJson);
        }
    }

    private void addData() {

    }

    public static void main(String[] args) {
        System.out.println("Initializing database...");
        String url = "jdbc:mysql://localhost:3306/airvoydb?useSSL=false";
        String user = "admin";
        String password = "admin123";
        DatabaseManager databaseManager = new DatabaseManager(url, user, password);

//        String query = "SELECT VERSION()";
//        try (Connection con = DriverManager.getConnection(url, user, password);
//             Statement st = con.createStatement();
//             ResultSet rs = st.executeQuery(query)) {
//            if (rs.next()) {
//                System.out.println(rs.getString(1));
//            }
//        } catch (SQLException e) {
//            logger.warn("Exception connecting to database: " + e.getMessage()
//                    + ", stack trace: " + Arrays.toString(e.getStackTrace()));
//        }
        System.out.println("Starting server...");
	    int port;
        try {
            port = Integer.parseInt(System.getenv("PORT"));
        } catch (NumberFormatException nfe) {
            port = 9000;
        }
        new Server(port).start();
    }

}
