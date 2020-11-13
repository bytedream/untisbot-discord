package org.bytedream.untisbot.untis;

import org.bytedream.untis4j.responseObjects.Timetable;

import java.sql.Time;
import java.time.LocalDate;
import java.util.ArrayList;

/**
 * Callback of {@link TimetableChecker#check(LocalDate)}
 *
 * @version 1.0
 * @since 1.0
 */
public class CheckCallback {

    private final Timetable allLessons;
    private final ArrayList<Timetable.Lesson> cancelled;
    private final ArrayList<Timetable.Lesson[]> moved;
    private final ArrayList<Timetable.Lesson> notCancelled;
    private final ArrayList<Timetable.Lesson[]> notMoved;

    /**
     * Initialize the {@link CheckCallback} class
     *
     * @param cancelled    all cancelled messages
     * @param moved        all moved messages
     * @param notCancelled all not cancelled messages
     * @param notMoved     all not moved messages
     * @since 1.0
     */
    public CheckCallback(Timetable allLessons, ArrayList<Timetable.Lesson> cancelled, ArrayList<Timetable.Lesson[]> moved, ArrayList<Timetable.Lesson> notCancelled, ArrayList<Timetable.Lesson[]> notMoved) {
        this.allLessons = allLessons;
        this.cancelled = cancelled;
        this.moved = moved;
        this.notCancelled = notCancelled;
        this.notMoved = notMoved;
    }

    /**
     * Returns all that were checked
     *
     * @return all that were checked
     * @since 1.0
     */
    public Timetable getAllLessons() {
        return allLessons;
    }

    /**
     * Returns all cancelled lessons
     *
     * @return all cancelled lessons
     * @since 1.0
     */
    public ArrayList<Timetable.Lesson> getCancelled() {
        return cancelled;
    }

    /**
     * Returns all moved lessons
     *
     * @return all moved lessons
     * @since 1.0
     */
    public ArrayList<Timetable.Lesson[]> getMoved() {
        return moved;
    }

    /**
     * Returns all not cancelled lessons
     *
     * @return all not cancelled lessons
     * @since 1.0
     */
    public ArrayList<Timetable.Lesson> getNotCancelled() {
        return notCancelled;
    }

    /**
     * Returns all not moved lessons
     *
     * @return all not moved lessons
     * @since 1.0
     */
    public ArrayList<Timetable.Lesson[]> getNotMoved() {
        return notMoved;
    }
}
