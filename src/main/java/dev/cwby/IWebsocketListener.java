package dev.cwby;

public interface IWebsocketListener {

    void onMessage(String message);

    void onClose(int code, String reason);

    void onError(String error);

    void onOpen(int code, String reason);

}