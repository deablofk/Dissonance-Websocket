package dev.cwby;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        TestWebsocketListener client = new TestWebsocketListener("localhost", 8765);
        client.startBlocking();
        client.close();
    }

}