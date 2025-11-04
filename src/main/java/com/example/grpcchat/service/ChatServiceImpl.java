package com.example.grpcchat.service;

import com.example.grpcchat.*;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConcurrentHashMap<String, StreamObserver<ChatMessage>> connectedClients =
            new ConcurrentHashMap<>();
    private final List<ChatMessage> messageHistory = new CopyOnWriteArrayList<>();

    @Override
    public void sendMessage(ChatMessage request, StreamObserver<MessageResponse> responseObserver) {
        System.out.println("Unary RPC: Сообщение от " + request.getUserId() + ": " + request.getText());

        // Создаем новое сообщение
        ChatMessage message = ChatMessage.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setUserId(request.getUserId())
                .setText(request.getText())
                .setTimestamp(LocalDateTime.now().format(formatter))
                .setType(MessageType.USER_MESSAGE)
                .build();

        messageHistory.add(message);
        broadcastMessage(message);

        MessageResponse response = MessageResponse.newBuilder()
                .setId(message.getId())
                .setSuccess(true)
                .setMessage("Сообщение доставлено")
                .setTimestamp(LocalDateTime.now().format(formatter))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void receiveMessages(ConnectRequest request, StreamObserver<ChatMessage> responseObserver) {
        String clientId = request.getUserId().isEmpty() ? UUID.randomUUID().toString() : request.getUserId();
        System.out.println("Server Streaming: Клиент " + clientId + " подключился");

        connectedClients.put(clientId, responseObserver);

        try {
            // Приветственное сообщение
            ChatMessage welcomeMessage = ChatMessage.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setUserId("System")
                    .setText("Добро пожаловать в чат, " + clientId + "!")
                    .setTimestamp(LocalDateTime.now().format(formatter))
                    .setType(MessageType.SYSTEM_MESSAGE)
                    .build();
            responseObserver.onNext(welcomeMessage);

            // История сообщений
            for (ChatMessage msg : messageHistory) {
                responseObserver.onNext(msg);
            }

            // Оставляем соединение открытым, не вызываем onCompleted()
            // Сообщения будут приходить через broadcastMessage

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }


    @Override
    public StreamObserver<ChatMessage> chatStream(StreamObserver<ChatMessage> responseObserver) {
        String clientId = UUID.randomUUID().toString();
        System.out.println("Bidirectional Streaming: Клиент " + clientId + " подключился");

        connectedClients.put(clientId, responseObserver);

        // Приветственное сообщение
        ChatMessage welcomeMessage = ChatMessage.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setUserId("System")
                .setText("Добро пожаловать в интерактивный чат!")
                .setTimestamp(LocalDateTime.now().format(formatter))
                .setType(MessageType.SYSTEM_MESSAGE)
                .build();
        responseObserver.onNext(welcomeMessage);

        return new StreamObserver<ChatMessage>() {
            @Override
            public void onNext(ChatMessage message) {
                String senderId = message.getUserId().isEmpty() ? clientId : message.getUserId();

                ChatMessage newMessage = ChatMessage.newBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setUserId(senderId)
                        .setText(message.getText())
                        .setTimestamp(LocalDateTime.now().format(formatter))
                        .setType(MessageType.USER_MESSAGE)
                        .build();

                messageHistory.add(newMessage);
                broadcastMessage(newMessage);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Ошибка в bidirectional: " + t.getMessage());
                connectedClients.remove(clientId);

                ChatMessage userLeftMessage = ChatMessage.newBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setUserId("System")
                        .setText("Пользователь " + clientId + " отключился")
                        .setTimestamp(LocalDateTime.now().format(formatter))
                        .setType(MessageType.NOTIFICATION)
                        .build();
                broadcastMessage(userLeftMessage);
            }

            @Override
            public void onCompleted() {
                System.out.println("Клиент " + clientId + " отключился");
                connectedClients.remove(clientId);
                responseObserver.onCompleted();
            }
        };
    }


    private void broadcastMessage(ChatMessage message) {
        connectedClients.forEach((clientId, observer) -> {
            try {
                observer.onNext(message);
            } catch (Exception e) {
                System.err.println("Ошибка отправки клиенту " + clientId);
                connectedClients.remove(clientId);
            }
        });
    }

    public int getConnectedClientsCount() {
        return connectedClients.size();
    }

    public int getMessageHistoryCount() {
        return messageHistory.size();
    }
}