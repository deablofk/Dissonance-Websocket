package dev.cwby;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WebSocketClient {

    private Socket socket;
    private final String host;
    private final int port;
    // maybe use a faster random alternative for improved performance (it is already fast as fuck)
    private final Random random = new Random();
    private final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private final Map<String, String> HEADERS = new HashMap<>();
    private boolean open = false;

    public WebSocketClient(String host, int port) {
        this.host = host;
        this.port = port;
        HEADERS.put("Host", this.host + ":" + this.port);
        HEADERS.put("Upgrade", "websocket");
        HEADERS.put("Connection", "Upgrade");
        HEADERS.put("Sec-WebSocket-Key", generateWebSocketKey());
        HEADERS.put("Sec-WebSocket-Version", "13");
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }

    public void connect() throws IOException {
        this.socket = new Socket(host, port);

        HandShakeResponse response = sendHandShake(HEADERS);
        setOpen(response.success());

        System.out.println("upgraded to websocket");
    }

    public void sendMessage(String message) throws IOException {
        if (!open) {
            throw new IllegalStateException("WebSocketClient socket not open");
        }

        byte[] frameBytes = generateFrame(message.getBytes(StandardCharsets.UTF_8));
        for (byte frameByte : frameBytes) {
            socket.getOutputStream().write(frameByte);
        }
        socket.getOutputStream().flush();
    }

    private String buildHandshakeRequest(Map<String, String> headersMap) {
        var strBuilder = new StringBuilder("GET /ws HTTP/1.1\r\n");
        for (Map.Entry<String, String> header : headersMap.entrySet()) {
            strBuilder.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        strBuilder.append("\r\n");
        return strBuilder.toString();
    }

    private HandShakeResponse sendHandShake(Map<String, String> headersMap) {
        try {
            var out = new PrintWriter(socket.getOutputStream());
            var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String handshakeRequest = buildHandshakeRequest(headersMap);
            out.write(handshakeRequest);
            out.flush();

            String responseHeader = in.readLine();
            boolean success = responseHeader.equals("HTTP/1.1 101 Switching Protocols");
            return new HandShakeResponse(success, responseHeader);
        } catch (IOException e) {
            return new HandShakeResponse(false, e.getMessage());
        }
    }

    private String generateWebSocketKey() {
        byte[] keyBytes = new byte[16];
        random.nextBytes(keyBytes);
        return BASE64_ENCODER.encodeToString(keyBytes);
    }

    private byte[] generateFrame(byte[] payload) {
        int payloadLength = payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(6 + payloadLength);

        byte finRsvOpcode = (byte) (0b10000000 | WebSocketCode.TEXT.getCode());
        byte maskAndPayloadLength = (byte) (0b10000000 | payloadLength);

        buffer.put(finRsvOpcode);
        buffer.put(maskAndPayloadLength);

        int maskKey = random.nextInt();
        buffer.putInt(maskKey);
        buffer.put(maskPayload(payload, maskKey));

        return buffer.array();
    }

    private byte[] maskPayload(byte[] payload, int maskKey) {
        byte[] maskBytes = Utils.intToByteArray(maskKey);
        byte[] maskedPayload = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            maskedPayload[i] = ((byte) (payload[i] ^ maskBytes[i % 4]));
        }
        return maskedPayload;
    }

}