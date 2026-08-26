package pollsystem.ai;

import pollsystem.model.Question;
import pollsystem.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * יצירת שאלות סקר אוטומטית באמצעות ChatGPT API (סעיף 3: "יצירה באמצעות
 * ChatGPT API"). הפלט נאכף להתאים לאילוצי סעיף 3: 1-3 שאלות, 2-4 אפשרויות
 * לכל שאלה - כל חריגה נחתכת/מסוננת כאן, ולא מגיעה כמו שהיא ל-GUI.
 * <p>
 * הפנייה עוברת דרך פרוקסי של המרצה (ולא ישירות ל-OpenAI), עם טוקן קורס
 * ייעודי - כך שאין צורך במפתח OpenAI אישי ואין חיוב/מגבלת מכסה אישית.
 */
public final class ChatGptPollGenerator {
    private static final String ENDPOINT = "https://shaitest-production-3066.up.railway.app/api-request";

    // הפרויקט (במכוון, ראו README) לא משתמש בספריות חיצוניות כמו Gson - לכן
    // כאן משתמשים ב-Json util הפנימי של הפרויקט במקום Gson, בניגוד לדוגמה
    // המקורית ששלח המרצה.
    private final String proxyToken;
    private final String model; // נשמר לתאימות עם הקוד הקורא; הפרוקסי הזה לא מקבל פרמטר מודל
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public ChatGptPollGenerator(String proxyToken, String model) {
        this.proxyToken = proxyToken;
        this.model = model;
    }

    /**
     * מייצר שאלות סקר בנושא נתון. זורק PollGenerationException עם הודעה ברורה
     * בכשל רשת, כשל API, או תשובה שלא ניתן היה לפרסר/לתקף - כדי שממשק ה-Swing
     * יציג הודעת שגיאה מובנת (בהתאם לדגש ה-UX על משוב שגיאה במפרט).
     */
    public List<Question> generateQuestions(String topic, int questionCount) {
        if (topic == null || topic.isBlank()) {
            throw new PollGenerationException("יש להזין נושא לסקר");
        }
        int desired = Math.max(1, Math.min(3, questionCount));

        String prompt = "You are a survey generator. " +
                "Generate exactly " + desired + " multiple-choice poll question(s) about the topic: \"" + topic +
                "\". Each question must have between 2 and 4 short answer options. " +
                "Write the question text and options in Hebrew. " +
                "Respond ONLY with a raw JSON array matching this exact format: " +
                "[{\"text\": \"שאלה?\", \"options\": [\"אפשרות 1\", \"אפשרות 2\"]}]. " +
                "Do not include markdown code blocks or any conversational text.";

        String responseBody;
        try {
            String encodedText = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String url = ENDPOINT + "?token=" + proxyToken + "&text=" + encodedText;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new PollGenerationException("שגיאת Proxy API (קוד " + response.statusCode() + "): " + response.body());
            }
            responseBody = response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new PollGenerationException("שגיאת רשת בפנייה ל-Proxy API בכתובת " + ENDPOINT, e);
        }

        return parseQuestions(responseBody, desired);
    }

    private List<Question> parseQuestions(String responseBody, int desired) {
        // הפרוקסי לפעמים עוטף את תשובת ה-AI בעטיפת markdown/escaping - מנקים
        // באותו אופן כמו בדוגמה ששלח המרצה, לפני חילוץ מערך ה-JSON.
        String content = responseBody
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("```json", "")
                .replace("```", "");

        // אם הפרוקסי החזיר שגיאה מפורשת ({"error":true,...}) ולא מערך שאלות,
        // עדיף להציג את זה כשגיאה ברורה מאשר לנסות לפרסר אותו כשאלות.
        if (content.contains("\"error\"") && content.contains("true") && !content.trim().startsWith("[")) {
            throw new PollGenerationException("שגיאה מה-Proxy API: " + responseBody);
        }

        int startIndex = content.indexOf('[');
        int endIndex = content.lastIndexOf(']');
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            throw new PollGenerationException("לא נמצא מערך JSON בתשובת ה-API: " + responseBody);
        }
        content = content.substring(startIndex, endIndex + 1);

        List<Object> questionsRaw;
        try {
            Object parsed = Json.parse(content);
            questionsRaw = Json.asList(parsed);
        } catch (Json.JsonException e) {
            throw new PollGenerationException("לא ניתן היה לפענח את פלט ה-API כ-JSON תקין", e);
        }
        if (questionsRaw == null || questionsRaw.isEmpty()) {
            throw new PollGenerationException("ה-API לא החזיר שאלות");
        }

        List<Question> result = new ArrayList<>();
        for (Object qObj : questionsRaw) {
            Map<String, Object> qMap = Json.asMap(qObj);
            if (qMap == null) continue;
            String text = Json.asString(qMap.get("text"));
            List<Object> optsRaw = Json.asList(qMap.get("options"));
            if (text == null || text.isBlank() || optsRaw == null) continue;

            List<String> options = new ArrayList<>();
            for (Object o : optsRaw) {
                String s = Json.asString(o);
                if (s != null && !s.isBlank()) options.add(s.trim());
            }
            if (options.size() > 4) options = options.subList(0, 4); // סעיף 3: אכיפת עד 4 אפשרויות
            if (options.size() < 2) continue;                        // סעיף 3: מינימום 2 אפשרויות

            result.add(new Question(text.trim(), options));
            if (result.size() >= desired) break; // סעיף 3: לכל היותר 3 שאלות
        }

        if (result.isEmpty()) {
            throw new PollGenerationException("ה-API לא החזיר אף שאלה תקינה (בין 2 ל-4 אפשרויות)");
        }
        return result;
    }

    public static final class PollGenerationException extends RuntimeException {
        public PollGenerationException(String message) { super(message); }
        public PollGenerationException(String message, Throwable cause) { super(message, cause); }
    }
}
