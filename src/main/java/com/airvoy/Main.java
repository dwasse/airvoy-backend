package com.airvoy;

public class Main {

    public static void main(String[] args) {
        System.out.println("Initializing database...");
        String url = "jdbc:mysql://localhost:3306/airvoydb?useSSL=false";
        String user = "admin";
        String password = "admin123";
        DatabaseManager databaseManager = new DatabaseManager(url, user, password);
        System.out.println("Starting server...");
        int port;
        try {
            port = Integer.parseInt(System.getenv("PORT"));
        } catch (NumberFormatException nfe) {
            port = 9001;
        }
        Server server = new Server(port, databaseManager);
        server.start();
    }

}
