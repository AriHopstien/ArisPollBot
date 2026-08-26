package pollsystem.core;

import pollsystem.model.Community;
import pollsystem.model.Poll;

/**
 * "המוח" של המערכת - מקור האמת היחיד (single source of truth) שגם הבוט
 * וגם ה-Swing GUI ניגשים אליו. תהליך Java אחד, ללא בסיס נתונים חיצוני
 * וללא שרת נפרד - הבוט וה-GUI הם שני threads באותו תהליך שמשתפים את זה.
 */
public final class AppState {
    private static final AppState INSTANCE = new AppState();
    public static AppState getInstance() { return INSTANCE; }

    private final Community community = new Community();
    private final EventBus eventBus = new EventBus();
    private volatile Poll currentPoll; // null == אין סקר פעיל/ממתין כרגע

    private AppState() {}

    public Community getCommunity() { return community; }
    public EventBus getEventBus() { return eventBus; }

    public synchronized Poll getCurrentPoll() { return currentPoll; }
    public synchronized void setCurrentPoll(Poll poll) { this.currentPoll = poll; }
}
