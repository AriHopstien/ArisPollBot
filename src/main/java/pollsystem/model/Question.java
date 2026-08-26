package pollsystem.model;

import java.util.List;

/**
 * שאלה בסקר. אוכף את אילוץ סעיף 3: בין 2 ל-4 אפשרויות תשובה לכל שאלה.
 * (המגבלה של 1-3 שאלות לסקר נאכפת ברמת Poll, לא כאן.)
 */
public record Question(String text, List<String> options) {
    public Question {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("שאלה חייבת להכיל נוסח");
        }
        if (options == null || options.size() < 2 || options.size() > 4) {
            throw new IllegalArgumentException("שאלה חייבת להכיל בין 2 ל-4 אפשרויות תשובה");
        }
        options = List.copyOf(options);
    }
}
