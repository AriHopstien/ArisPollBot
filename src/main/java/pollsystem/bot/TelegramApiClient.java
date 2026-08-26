package pollsystem.bot;

import pollsystem.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * עטיפה דקה סביב Telegram Bot HTTP API (https://core.telegram.org/bots/api).
 * ללא ספריות חיצוניות - java.net.http.HttpClient הסטנדרטי + ה-JSON מיני-פרסר
 * שכתבנו ב-pollsystem.util.Json. זו החלטה מכוונת: הסביבה שבה נכתב הקוד הזה
 * לא הייתה מסוגלת להוריד תלויות מ-Maven Central, ולכן המימוש כולו נטול-תלויות.
 */
public final class TelegramApiClient {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public TelegramApiClient(String botToken) {
        this.baseUrl = "https://api.telegram.org/bot" + botToken + "/";
    }

    /** קריאה גנרית לכל method ב-API. readTimeoutSeconds צריך לעלות על timeout ה-long-polling עבור getUpdates. */
    public Map<String, Object> call(String method, Map<String, Object> params, int readTimeoutSeconds) {
        try {
            String body = Json.stringify(params);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + method))
                    .timeout(Duration.ofSeconds(readTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> parsed = Json.parseObject(response.body());
            Object ok = parsed.get("ok");
            if (!(ok instanceof Boolean b) || !b) {
                throw new TelegramApiException("שגיאת Telegram API ב-" + method + ": " + response.body());
            }
            return parsed;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new TelegramApiException("שגיאת רשת בקריאה ל-" + method, e);
        }
    }

    public Map<String, Object> call(String method, Map<String, Object> params) {
        return call(method, params, 30);
    }

    @SuppressWarnings("unchecked")
    public List<Object> getUpdates(long offset, int timeoutSeconds) {
        Map<String, Object> params = Json.obj();
        params.put("offset", offset);
        params.put("timeout", timeoutSeconds);
        params.put("allowed_updates", List.of("message", "callback_query"));
        Map<String, Object> response = call("getUpdates", params, timeoutSeconds + 15);
        Object result = response.get("result");
        return result instanceof List ? (List<Object>) result : List.of();
    }

    public void sendMessage(long chatId, String text, Object replyMarkup) {
        Map<String, Object> params = Json.obj();
        params.put("chat_id", chatId);
        params.put("text", text);
        if (replyMarkup != null) params.put("reply_markup", replyMarkup);
        call("sendMessage", params);
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public void editMessageText(long chatId, long messageId, String text, Object replyMarkup) {
        Map<String, Object> params = Json.obj();
        params.put("chat_id", chatId);
        params.put("message_id", messageId);
        params.put("text", text);
        if (replyMarkup != null) params.put("reply_markup", replyMarkup);
        call("editMessageText", params);
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> params = Json.obj();
        params.put("callback_query_id", callbackQueryId);
        if (text != null) params.put("text", text);
        call("answerCallbackQuery", params);
    }

    /** בונה מקלדת inline: שורה אחת לכל אפשרות תשובה - נקראות בקלות, לחיצה קשה לטעות (עיצוב UX). */
    public static Object buildOptionsKeyboard(List<String> options, String callbackPrefix) {
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Map<String, Object> button = Json.obj();
            button.put("text", options.get(i));
            button.put("callback_data", callbackPrefix + ":" + i);
            rows.add(List.of(button));
        }
        Map<String, Object> keyboard = Json.obj();
        keyboard.put("inline_keyboard", rows);
        return keyboard;
    }

    /** מקלדת ריקה - משמשת להסרת הכפתורים אחרי שנרשמה תשובה, כדי שלא ניתן יהיה ללחוץ שוב. */
    public static Object emptyKeyboard() {
        Map<String, Object> keyboard = Json.obj();
        keyboard.put("inline_keyboard", List.of());
        return keyboard;
    }

    public static final class TelegramApiException extends RuntimeException {
        public TelegramApiException(String message) { super(message); }
        public TelegramApiException(String message, Throwable cause) { super(message, cause); }
    }
}
