package dev.cwby;

import java.util.Map;

public record HandShakeResponse(boolean success, Map<String, String> headers) {
}
