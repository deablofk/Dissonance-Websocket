package dev.cwby;

public interface IWebsocketListener {

    void onMessage(String message);

    void onClose(WebSocketCode code);

    void onError(String error);

    void onOpen();

}