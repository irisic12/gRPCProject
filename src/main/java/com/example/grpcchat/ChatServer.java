package com.example.grpcchat;

import com.example.grpcchat.service.ChatServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class ChatServer {
    private final int port;
    private final Server server;
    private final ChatServiceImpl chatService;

    public ChatServer(int port) {
        this.port = port;
        this.chatService = new ChatServiceImpl();
        this.server = ServerBuilder.forPort(port)
                .addService(chatService)
                .build();
    }

    public void start() throws IOException {
        server.start();
        System.out.println("🚀 gRPC Chat Server запущен на порту " + port);
        System.out.println("📡 Сервисы:");
        System.out.println("   - SendMessage (Unary)");
        System.out.println("   - ReceiveMessages (Server Streaming)");
        System.out.println("   - ChatStream (Bidirectional Streaming)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("*** Выключение сервера ***");
            try {
                ChatServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("*** Сервер выключен ***");
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 50051;
        ChatServer server = new ChatServer(port);
        server.start();
        server.blockUntilShutdown();
    }
}