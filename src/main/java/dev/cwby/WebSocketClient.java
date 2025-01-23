package dev.cwby;

import java.io.*;
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

        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] frameBytes = generateFrame(messageBytes, true, WebSocketCode.TEXT);
        for (byte frameByte : frameBytes) {
            socket.getOutputStream().write(frameByte);
        }
        socket.getOutputStream().flush();
    }

    public void startListening() throws IOException {
        if (!open) {
            throw new IllegalStateException("WebSocketClient socket not open");
        }

        Thread thread = new Thread(() -> {
            try (InputStream in = socket.getInputStream()) {
                while (open) {
                    String message = readFrame(in);
                    System.out.println("Received message: " + message);
                }
            } catch (IOException e) {

            }
        });
        thread.start();
    }

    public void close() throws IOException {
        if (!open) {
            throw new IllegalStateException("WebSocketClient socket not open");
        }

        sendCloseCode(WebSocketCode.CONNECTION_CLOSE.getCode());
        setOpen(false);
    }

    private void sendCloseCode(int closeCode) throws IOException {
        byte[] closeCodeBytes = null;

        if (closeCode != -1) {
            closeCodeBytes = new byte[2];
            closeCodeBytes[0] = (byte) ((closeCode >> 8) & 0xFF);
            closeCodeBytes[1] = (byte) (closeCode & 0xFF);
        }

        byte[] frameBytes;

        if (closeCodeBytes != null) {
            frameBytes = generateFrame(closeCodeBytes, false, WebSocketCode.CONNECTION_CLOSE);
        } else {
            frameBytes = generateFrame(new byte[0], false, WebSocketCode.CONNECTION_CLOSE);
        }

        socket.getOutputStream().write(frameBytes);
        socket.getOutputStream().flush();
    }

    private String readFrame(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) {
            System.out.println("No more frames");
        }

        // TODO: properly handle FIN bit in websocket frames for proper fragmentation and multiplexing
        // boolean fin = (firstByte & 0x80) != 0;
        // int opcode = firstByte & 0x0F;

        int secondByte = in.read();
        boolean masked = (secondByte & 0x80) != 0;

        if (masked) {
            throw new IOException("received a masked frame from the server");
        }

        int payloadLength = secondByte & 0x7F;

        if (payloadLength == 126) {
            payloadLength = (in.read() << 8) | in.read();
        } else if (payloadLength == 127) {
            payloadLength = in.read() << 56 | in.read() << 48 | in.read() << 40 | in.read() << 32 | in.read() << 24 | in.read() << 16 | in.read() << 8 | in.read();
        }

        byte[] payload = new byte[payloadLength];

        int bytesRead = 0;

        while (bytesRead < payloadLength) {
            int read = in.read(payload, bytesRead, payloadLength - bytesRead);
            if (read == -1) {
                throw new IOException("No more data");
            }
            bytesRead += read;
        }

        return new String(payload, StandardCharsets.UTF_8);
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

    private byte[] generateFrame(byte[] payload, boolean fin, WebSocketCode code) {
        int payloadLength = payload.length;

        byte finRsvOpcode = (byte) ((fin ? 0b10000000 : 0b00000000) | code.getCode());

        ByteBuffer buffer;
        if (payloadLength <= 125) {
            buffer = ByteBuffer.allocate(6 + payloadLength);
            buffer.put(finRsvOpcode);
            buffer.put((byte) (0x80 | payloadLength));
        } else if (payloadLength <= 65535) {
            buffer = ByteBuffer.allocate(6 + payloadLength + 2);
            buffer.put(finRsvOpcode);
            buffer.put((byte) (0b10000000 | 126));
            buffer.putShort((short) payloadLength);  // Extended 16-bit length
        } else {
            buffer = ByteBuffer.allocate(6 + payloadLength + 8);
            buffer.put(finRsvOpcode);
            buffer.put((byte) (0b10000000 | 127));
            buffer.putLong(payloadLength);  // Extended 64-bit length
        }

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