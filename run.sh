#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
if [ ! -d out ]; then
    echo "לא נמצאה תיקיית out/ - מריץ build.sh קודם..."
    ./build.sh
fi
if [ ! -f config.properties ]; then
    echo "אזהרה: לא נמצא config.properties בתיקייה הנוכחית."
    echo "העתיקו את config.properties.example ל-config.properties ומלאו את המפתחות שלכם,"
    echo "או הגדירו את TELEGRAM_BOT_TOKEN ו-OPENAI_API_KEY כמשתני סביבה."
fi
java -cp out pollsystem.Main
