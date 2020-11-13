package org.bytedream.untisbot.untis;

import org.bytedream.untis4j.LoginException;
import org.bytedream.untis4j.RequestManager;
import org.bytedream.untis4j.Session;
import org.bytedream.untis4j.UntisUtils;
import org.bytedream.untis4j.responseObjects.Timetable;
import org.bytedream.untisbot.Main;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Class to check the untis timetable
 *
 * @version 1.0
 * @since 1.0
 */
public class TimetableChecker {

    private final Session session;
    private final int klasseId;
    private final LocalDate[] cancelledLessonsDay = new LocalDate[7];
    private final LocalDate[] ignoredLessonsDay = new LocalDate[7];
    private final LocalDate[] movedLessonsDay = new LocalDate[7];
    private final Timetable[] cancelledLessons = new Timetable[]{new Timetable(), new Timetable(), new Timetable(), new Timetable(), new Timetable(), new Timetable(), new Timetable()};
    private final Timetable[] ignoredLessons = new Timetable[]{new Timetable(), new Timetable(), new Timetable(), new Timetable(), new Timetable(), new Timetable(), new Timetable()};
    private final ArrayList<HashMap<Timetable.Lesson, Timetable.Lesson>> movedLessons = new ArrayList<>();

    /**
     * Sets all necessary configurations and connects to the untis account with the given untis credentials
     *
     * @param username   username of the untis account
     * @param password   user password of the untis account
     * @param server     the server from the school as URL
     * @param schoolName name of the school
     * @throws IOException if any {@link IOException} while the login occurs
     * @since 1.0
     */
    public TimetableChecker(String username, String password, String server, String schoolName, int klasseId) throws IOException {
        session = Session.login(username, password, server, schoolName);
        this.klasseId = klasseId;

        for (LocalDate[] localDates : new HashSet<LocalDate[]>() {{
            add(cancelledLessonsDay);
            add(ignoredLessonsDay);
            add(movedLessonsDay);
        }}) {
            for (int i = 0; i < 7; i++) {
                localDates[i] = LocalDate.now().plusDays(i + 1);
            }
        }

        for (int i = 0; i < 7; i++) {
            movedLessons.add(new HashMap<>());
        }
    }

    /**
     * Checks the timetable on a specific date. Automatically deletes cached lessons from the past, so you should not call the method in descending date order
     *
     * @param dateToCheck date which should be checked
     * @return {@link CheckCallback} with information about the timetable (if anything has changed)
     * @throws IOException if any {@link IOException} occurs
     * @since 1.0
     */
    public CheckCallback check(LocalDate dateToCheck) throws IOException {
        Timetable timetable = session.getTimetableFromKlasseId(dateToCheck, dateToCheck, klasseId);
        timetable.sortByStartTime();

        int dayOfWeekInArray = dateToCheck.getDayOfWeek().getValue() - 1;

        Timetable allCancelledLessons = cancelledLessons[dayOfWeekInArray];
        Timetable allIgnoredLessons = ignoredLessons[dayOfWeekInArray];
        HashMap<Timetable.Lesson, Timetable.Lesson> allMovedLessons = movedLessons.get(dayOfWeekInArray);

        Timetable totalLessons = new Timetable();
        ArrayList<Timetable.Lesson> cancelledLesson = new ArrayList<>();
        ArrayList<Timetable.Lesson[]> movedLesson = new ArrayList<>();
        ArrayList<Timetable.Lesson> notCancelledLessons = new ArrayList<>();
        ArrayList<Timetable.Lesson[]> notMovedLessons = new ArrayList<>();

        for (Timetable.Lesson lesson : timetable) {
            totalLessons.add(lesson);
            if (lesson.getCode() == UntisUtils.LessonCode.CANCELLED && !allCancelledLessons.contains(lesson) && !allIgnoredLessons.contains(lesson)) {
                Timetable specificLessons = timetable.searchByStartTime(lesson.getStartTime());
                specificLessons.remove(lesson);

                switch (specificLessons.size()) {
                    case 0: // lesson is cancelled
                        allCancelledLessons.add(lesson);
                        cancelledLesson.add(lesson);
                        break;
                    case 1: // lesson is maybe moved
                        if (specificLessons.get(0).getCode() == UntisUtils.LessonCode.IRREGULAR) { // lesson is moved
                            Timetable.Lesson irregularLesson = specificLessons.get(0);

                            for (Timetable.Lesson lesson1 : timetable.searchByTeachers(irregularLesson.getTeachers())) {
                                if (lesson1.getCode() == UntisUtils.LessonCode.CANCELLED && !allIgnoredLessons.contains(lesson1)) {
                                    allIgnoredLessons.add(lesson1);
                                    allCancelledLessons.remove(lesson1);

                                    allMovedLessons.put(lesson, lesson1);
                                    movedLesson.add(new Timetable.Lesson[]{lesson, lesson1});
                                    break;
                                }
                            }
                        } else { // lesson is not moved but cancelled
                            allCancelledLessons.add(lesson);
                            cancelledLesson.add(lesson);
                        }
                        break;
                }
            } else if (lesson.getCode() == UntisUtils.LessonCode.IRREGULAR && timetable.searchByStartTime(lesson.getStartTime()).size() == 1 && !allIgnoredLessons.contains(lesson)) {
                // lesson is maybe moved
                for (Timetable.Lesson lesson1 : timetable) {
                    // checks if another lesson exist with the same 'stats' and if it's cancelled
                    if (lesson1.getCode() == UntisUtils.LessonCode.CANCELLED && !allIgnoredLessons.contains(lesson1) && lesson.getSubjects().containsAll(lesson1.getSubjects())) {
                        allIgnoredLessons.add(lesson1);

                        allMovedLessons.put(lesson, lesson1);
                        movedLesson.add(new Timetable.Lesson[]{lesson, lesson1});
                        break;
                    }
                }
            } else if (allMovedLessons.containsKey(lesson) && lesson.getCode() == UntisUtils.LessonCode.REGULAR) { // checks if a moved lesson takes place again
                Timetable.Lesson value = allMovedLessons.get(lesson);
                allIgnoredLessons.remove(value);

                allMovedLessons.remove(lesson);
                notMovedLessons.add(new Timetable.Lesson[]{lesson, value});
                break;
            } else if (allCancelledLessons.contains(lesson) && lesson.getCode() == UntisUtils.LessonCode.REGULAR) { // checks if a cancelled lesson takes place again
                allCancelledLessons.remove(lesson);
                notCancelledLessons.add(lesson);
                break;
            }
        }

        if (cancelledLessonsDay[dayOfWeekInArray].compareTo(dateToCheck) > 0) {
            cancelledLessonsDay[dayOfWeekInArray] = dateToCheck;
        }
        if (ignoredLessonsDay[dayOfWeekInArray].compareTo(dateToCheck) > 0) {
            ignoredLessonsDay[dayOfWeekInArray] = dateToCheck;
        }
        if (movedLessonsDay[dayOfWeekInArray].compareTo(dateToCheck) > 0) {
            movedLessonsDay[dayOfWeekInArray] = dateToCheck;
        }

        cancelledLessons[dayOfWeekInArray] = allCancelledLessons;
        ignoredLessons[dayOfWeekInArray] = allIgnoredLessons;
        movedLessons.remove(dayOfWeekInArray);
        movedLessons.add(dayOfWeekInArray, allMovedLessons);

        return new CheckCallback(totalLessons, cancelledLesson, movedLesson, notCancelledLessons, notMovedLessons);
    }

    /**
     * Returns the session
     *
     * @return the session
     * @since 1.0
     */
    public Session getSession() {
        return session;
    }

}
