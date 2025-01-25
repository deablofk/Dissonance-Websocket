package dev.cwby;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws InterruptedException, IOException {
        TestWebsocketListener client = new TestWebsocketListener("localhost", 8765, true, false);
        client.startBlocking();
        client.close();
    }
}
