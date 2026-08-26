#!/usr/bin/env bash
# בנייה ישירה עם javac, בלי Maven. שימושי אם אין גישה ל-Maven Central,
# או פשוט כדי לא להסתבך - כל מה שנדרש הוא JDK 17+.
set -e
cd "$(dirname "$0")"
echo "מקמפל את הפרויקט..."
mkdir -p out
javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
echo "קומפילציה הושלמה בהצלחה. קבצי ה-class נמצאים ב-out/"
