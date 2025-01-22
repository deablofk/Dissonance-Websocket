package dev.cwby;

public enum WebSocketCode {
    CONTINUATION(0x0, "Continuation Frame"),
    TEXT(0x1, "Text Frame"),
    BINARY(0x2, "Binary Frame"),
    RESERVED_NON_CONTROL_3(0x3, "Reserved Non-Control Frame"),
    RESERVED_NON_CONTROL_4(0x4, "Reserved Non-Control Frame"),
    RESERVED_NON_CONTROL_5(0x5, "Reserved Non-Control Frame"),
    RESERVED_NON_CONTROL_6(0x6, "Reserved Non-Control Frame"),
    RESERVED_NON_CONTROL_7(0x7, "Reserved Non-Control Frame"),
    CONNECTION_CLOSE(0x8, "Connection Close"),
    PING(0x9, "Ping"),
    PONG(0xA, "Pong"),
    RESERVED_CONTROL_B(0xB, "Reserved Control Frame"),
    RESERVED_CONTROL_C(0xC, "Reserved Control Frame"),
    RESERVED_CONTROL_D(0xD, "Reserved Control Frame"),
    RESERVED_CONTROL_E(0xE, "Reserved Control Frame"),
    RESERVED_CONTROL_F(0xF, "Reserved Control Frame");

    private final int code;
    private final String description;

    WebSocketCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static WebSocketCode fromCode(int code) {
        for (WebSocketCode opcode : values()) {
            if (opcode.code == code) {
                return opcode;
            }
        }
        throw new IllegalArgumentException("Unknown WebSocket opcode: " + code);
    }
}
