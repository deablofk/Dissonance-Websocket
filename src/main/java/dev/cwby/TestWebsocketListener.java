package dev.cwby;

import java.io.IOException;

public class TestWebsocketListener extends WebSocketClient {

    public TestWebsocketListener(String host, int port, boolean deflate, boolean tls) {
        super(host, port, deflate, tls);
    }

    @Override
    public void onMessage(String message) {
        System.out.println("onMessage: " + message);
    }

    @Override
    public void onClose(WebSocketCode code) {
        System.out.println("onClose: " + code.getDescription());
    }

    @Override
    public void onError(String error) {
        System.out.println("onError: " + error);
    }

    @Override
    public void onOpen() {
        System.out.println("onOpen: websocket opened");
        try {
            Thread.sleep(10000);
            sendMessage("you mom");
            close();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
