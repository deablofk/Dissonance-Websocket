package dev.cwby;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        try {
            WebSocketClient client = new WebSocketClient("localhost", 8765);
            client.connect();
            long count = 0;
            while (true) {
                client.sendMessage("tubarão " + count);
                Thread.sleep(1000);
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}