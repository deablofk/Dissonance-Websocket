package dev.cwby;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public abstract class WebSocketClient implements IWebsocketListener {

    private Socket socket;
    private final String host;
    private final int port;
    private boolean open = false;
    private static final Random random = new Random();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Map<String, String> HEADERS = new HashMap<>();
    private boolean deflateACK = false;
    private final boolean tls;

    public WebSocketClient(String host, int port, boolean deflate, boolean tls) {
        this.host = host;
        this.port = port;
        HEADERS.put("Host", this.host + ":" + this.port);
        HEADERS.put("Upgrade", "websocket");
        HEADERS.put("Connection", "Upgrade");
        HEADERS.put("Sec-WebSocket-Key", generateWebSocketKey());
        HEADERS.put("Sec-WebSocket-Version", "13");
        if (deflate) {
            HEADERS.put("Sec-WebSocket-Extensions", "permessage-deflate");
        }
        this.tls = tls;
    }

    private void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }

    private Socket createTLSSocket() throws IOException {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            return factory.createSocket(host, port);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private void connect() throws IOException {
        if (this.tls) {
            this.socket = createTLSSocket();
        } else {
            this.socket = new Socket(host, port);
        }
        HandShakeResponse response = sendHandShake(HEADERS);
        setOpen(response.success());

        onOpen();
    }

    public void sendMessage(String message) throws IOException {
        if (!open) {
            throw new IllegalStateException("WebSocketClient socket not open");
        }

        byte[] messageBytes = deflateACK ? compress(message.getBytes(StandardCharsets.UTF_8)) : message.getBytes(StandardCharsets.UTF_8);
        byte[] frameBytes = generateFrame(messageBytes, true, WebSocketCode.TEXT);
        for (byte frameByte : frameBytes) {
            socket.getOutputStream().write(frameByte);
        }
        socket.getOutputStream().flush();
    }

    public Thread start() throws IOException {
        if (open) {
            throw new IllegalStateException("WebSocketClient already open");
        } else {
            connect();
        }

        Thread thread = new Thread(() -> {
            try (InputStream in = socket.getInputStream()) {
                while (open) {
                    String message = readFrame(in);
                    onMessage(message);
                }
            } catch (IOException _) {
            }
        });
        thread.start();
        return thread;
    }

    public void startBlocking() throws InterruptedException, IOException {
        Thread thread = start();
        thread.join();
    }

    public void close() throws IOException {
        if (!open) {
            return;
        }

        sendCloseCode(1000);
        setOpen(false);

        onClose(WebSocketCode.CONNECTION_CLOSE);
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
            frameBytes = generateFrame(closeCodeBytes, true, WebSocketCode.CONNECTION_CLOSE);
        } else {
            frameBytes = generateFrame(new byte[0], true, WebSocketCode.CONNECTION_CLOSE);
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

        if (deflateACK) {
            return new String(decompress(payload), StandardCharsets.UTF_8);
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

            String httpLineHeader = in.readLine();
            Map<String, String> responseHeaders = new HashMap<>();

            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String[] values = line.split(":", 2);
                System.out.println(line);
                responseHeaders.put(values[0].toLowerCase().trim(), values[1].trim());
            }

            String value = responseHeaders.get("Sec-WebSocket-Extensions".toLowerCase());
            System.out.println(value);
            if (value != null && value.contains("permessage-deflate")) {
                System.out.println("deflate ack handshake received");
                this.deflateACK = true;
            }

            boolean success = httpLineHeader.equals("HTTP/1.1 101 Switching Protocols");
            return new HandShakeResponse(success, responseHeaders);
        } catch (IOException e) {
            onError(e.getMessage());
            return new HandShakeResponse(false, Map.of());
        }
    }

    private String generateWebSocketKey() {
        byte[] keyBytes = new byte[16];
        random.nextBytes(keyBytes);
        return BASE64_ENCODER.encodeToString(keyBytes);
    }

    private byte[] generateFrame(byte[] payload, boolean fin, WebSocketCode code) {
        int payloadLength = payload.length;

        byte finRsvOpcode = (byte) ((fin ? 0b10000000 : 0b00000000) | ((deflateACK && code != WebSocketCode.CONNECTION_CLOSE) ? 0b01000000 : 0b00000000) | code.getCode());

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

    private byte[] compress(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];

        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        deflater.end();
        return outputStream.toByteArray();
    }

    private byte[] decompress(byte[] data) throws IOException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished() && !inflater.needsInput()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
        } catch (DataFormatException e) {
            throw new IOException("Failed to decompress WebSocket frame", e);
        }
        inflater.end();
        return outputStream.toByteArray();
    }

}