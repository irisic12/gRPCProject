package com.example.grpcchat.client;

import com.example.grpcchat.ChatMessage;
import com.example.grpcchat.MessageResponse;
import com.example.grpcchat.ConnectRequest;
import com.example.grpcchat.MessageType;
import com.example.grpcchat.ChatServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ChatClient {
    private final ManagedChannel channel;
    private final ChatServiceGrpc.ChatServiceBlockingStub blockingStub;
    private final ChatServiceGrpc.ChatServiceStub asyncStub;
    private final String userId;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ChatClient(String host, int port, String userId) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        this.asyncStub = ChatServiceGrpc.newStub(channel);
        this.userId = userId;
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    // Unary вызов - отправка сообщения
    public void sendMessage(String text) {
        ChatMessage message = ChatMessage.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setUserId(userId)
                .setText(text)
                .setTimestamp(LocalDateTime.now().format(formatter))
                .setType(MessageType.USER_MESSAGE)
                .build();

        MessageResponse response = blockingStub.sendMessage(message);
        System.out.println("✅ Ответ сервера: " + response.getMessage());
    }

    // Server streaming - получение сообщений
    public void receiveMessages() {
        ConnectRequest request = ConnectRequest.newBuilder()
                .setUserId(userId)
                .build();

        try {
            blockingStub.receiveMessages(request)
                    .forEachRemaining(message -> {
                        System.out.printf("📨 [%s] %s: %s\n",
                                message.getTimestamp(),
                                message.getUserId(),
                                message.getText());
                    });
        } catch (Exception e) {
            System.err.println("Ошибка при получении сообщений: " + e.getMessage());
        }
    }

    // Bidirectional streaming - интерактивный чат
    public void startChat() throws InterruptedException {
        CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<ChatMessage> requestObserver =
                asyncStub.chatStream(new StreamObserver<ChatMessage>() {

                    @Override
                    public void onNext(ChatMessage message) {
                        System.out.printf("💬 [%s] %s: %s\n",
                                message.getTimestamp(),
                                message.getUserId(),
                                message.getText());
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.err.println("Ошибка в чате: " + t.getMessage());
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println("Чат завершен сервером");
                        finishLatch.countDown();
                    }
                });

        // Чтение сообщений с консоли
        try (Scanner scanner = new Scanner(System.in)) {

            while (scanner.hasNextLine()) {
                String text = scanner.nextLine().trim();
                if (text.isEmpty()) continue;

                ChatMessage message = ChatMessage.newBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setUserId(userId)
                        .setText(text)
                        .setTimestamp(LocalDateTime.now().format(formatter))
                        .setType(MessageType.USER_MESSAGE)
                        .build();

                requestObserver.onNext(message);
            }
        } catch (Exception e) {
            requestObserver.onError(e);
        } finally {
            requestObserver.onCompleted();
        }

        finishLatch.await(1, TimeUnit.MINUTES);
    }

    public static void main(String[] args) throws Exception {
        String userId = args.length > 0 ? args[0] : "JavaClient-" + UUID.randomUUID().toString().substring(0, 8);
        ChatClient client = new ChatClient("localhost", 50051, userId);

        try {
            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.println("\n=== gRPC Chat Client ===");
                System.out.println("1. Отправить сообщение (Unary)");
                System.out.println("2. Получать сообщения (Server Streaming)");
                System.out.println("3. Интерактивный чат (Bidirectional)");
                System.out.println("4. Выход");
                System.out.print("Выберите опцию: ");

                String choice = scanner.nextLine();

                switch (choice) {
                    case "1":
                        System.out.print("Введите сообщение: ");
                        String text = scanner.nextLine();
                        client.sendMessage(text);
                        break;
                    case "2":
                        System.out.println("Подключаемся к потоку сообщений...");
                        client.receiveMessages();
                        break;
                    case "3":
                        System.out.println("Запуск интерактивного чата...");
                        client.startChat();
                        break;
                    case "4":
                        System.out.println("Выход...");
                        client.shutdown();
                        return;
                    default:
                        System.out.println("Неверная опция");
                }
            }
        } finally {
            client.shutdown();
        }
    }
}
