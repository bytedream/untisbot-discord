package org.bytedream.untisbot.discord;

import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bytedream.untis4j.Session;
import org.bytedream.untis4j.responseObjects.Klassen;
import org.bytedream.untis4j.responseObjects.Teachers;
import org.bytedream.untis4j.responseObjects.TimeUnits;
import org.bytedream.untis4j.responseObjects.Timetable;
import org.bytedream.untisbot.Crypt;
import org.bytedream.untisbot.Main;
import org.bytedream.untisbot.Utils;
import org.bytedream.untisbot.data.Data;
import org.bytedream.untisbot.data.DataConnector;
import org.bytedream.untisbot.data.StoreType;
import org.bytedream.untisbot.untis.CheckCallback;
import org.bytedream.untisbot.untis.TimetableChecker;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Adapter to handle all events
 *
 * @version 1.1
 * @since 1.0
 */
public class DiscordCommandListener extends ListenerAdapter {

    private final DataConnector.Guild guildDataConnector;
    private final DataConnector.Stats statsDataConnector;
    private final JSONObject languages;

    private final HashMap<Long, Session> allUntisSessions = new HashMap<>();
    private final HashMap<Long, Timer> allTimetableChecker = new HashMap<>();
    private final Logger logger = Main.getLogger();

    private final HashMap<Long, LocalDateTime> dataUpdated = new HashMap<>();

    /**
     * Sets up the adapter
     *
     * @param storeType type how to store the given untis data {@link StoreType}
     * @param crypt     {@link Crypt} object to encrypt all passwords from the untis accounts
     * @param languages {@link JSONObject} containing different languages to print out when the timetable is checked
     * @since 1.0
     */
    public DiscordCommandListener(StoreType storeType, Crypt crypt, JSONObject languages) {
        DataConnector dataConnector = new DataConnector(storeType, crypt);

        guildDataConnector = dataConnector.guildConnector();
        statsDataConnector = dataConnector.statsConnector();
        this.languages = languages;

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN);
    }

    /**
     * Runs a command
     *
     * @param guild guild from which the command came
     * @param channel channel from which the command came
     * @param permission if true, commands which needs (admin) permission to run, can be executed
     * @param command command to execute
     * @param args extra arguments for the command
     *
     * @since 1.1
     */
    private void runCommand(Guild guild, TextChannel channel, boolean permission, String command, String[] args) {
        long guildId = guild.getIdLong();
        String guildName = guild.getName();
        Data.Guild data = guildDataConnector.get(guildId);

        switch (command) {
            case "timetable": // `timetable [day | [day.month] | [day.month.year]] [class name]` command
                if (args.length < 3) {
                    Session session = allUntisSessions.get(guildId);
                    LocalDate now = LocalDate.now();
                    Short classId = null;
                    LocalDate date = null;

                    if (data.getServer() == null && data.getSchool() == null) {
                        channel.sendMessage("Please set your data with the `data` command first, before you use this command. Type `" + data.getPrefix() + "help data` to get information").queue();
                        return;
                    }

                    if (args.length == 0) {
                        classId = data.getKlasseId();
                        date = now;
                    } else {
                        for (String arg : args) {
                            if (date == null) {
                                Integer number = null;
                                try {
                                    number = Integer.parseInt(arg);
                                } catch (NumberFormatException ignore) {
                                }
                                if (number != null && number <= 31 && number >= 1) {
                                    date = LocalDate.of(now.getYear(), now.getMonth(), number);
                                    continue;
                                } else if (arg.contains(".")) {
                                    String[] splitDate = args[0].split("\\.");
                                    try {
                                        switch (splitDate.length) {
                                            case 1:
                                                date = LocalDate.of(now.getYear(), now.getMonth(), Integer.parseInt(splitDate[0]));
                                                break;
                                            case 2:
                                                date = LocalDate.of(now.getYear(), Integer.parseInt(splitDate[1]), Integer.parseInt(splitDate[0]));
                                                break;
                                            case 3:
                                                date = LocalDate.of(Integer.parseInt(splitDate[2]), Integer.parseInt(splitDate[1]), Integer.parseInt(splitDate[0]));
                                                break;
                                            default:
                                                channel.sendMessage("Couldn't get date. Type `" + data.getPrefix() + "help timetable` for help").queue();
                                        }
                                        continue;
                                    } catch (NumberFormatException e) {
                                        channel.sendMessage("Couldn't get date. Type `" + data.getPrefix() + "help timetable` for help").queue();
                                        return;
                                    }
                                }
                            }
                            System.out.println("sss");
                            try {
                                classId = (short) session.getKlassen().findByName(arg).getId();
                            } catch (IOException e) {
                                logger.warn(guildId + " ran into an exception while trying to receive classes for a timetable", e);
                                channel.sendMessage("Couldn't search the class. Try again (later) or contact my author <@650417934073593886>, if the problem won't go away").queue();
                            } catch (NullPointerException e) {
                                channel.sendMessage("Couldn't find any class with the name '" + arg + "'").queue();
                            }
                        }
                    }
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setColor(new Color(138, 43, 226));
                    JSONObject language;
                    if (data.getLanguage() == null) {
                        language = languages.getJSONObject("en");
                    } else {
                        language = languages.getJSONObject(data.getLanguage());
                    }
                    String className = "-";
                    try {
                        className = session.getKlassen().findById(data.getKlasseId()).getName();
                    } catch (IOException ignore) {}
                    String finalClassName = className; // yea java...
                    LocalDate finalDate = date; // yea java part two...
                    embedBuilder.setTitle(Utils.advancedFormat(language.getString("timetable-title"), new HashMap<String, Object>() {{
                        put("class", finalClassName);
                        put("date", finalDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                    }}));

                    LocalTime lastStartTime = LocalTime.MIN;
                    boolean multipleLessonAtOnce = false;
                    TreeMap<LocalTime, ArrayList<Timetable.Lesson>> lessons = new TreeMap<>();
                    try {
                        for (Timetable.Lesson lesson : Timetable.sortByStartTime(allUntisSessions.get(guildId).getTimetableFromKlasseId(date, date, classId))) {
                            lessons.putIfAbsent(lesson.getStartTime(), new ArrayList<>());
                            lessons.get(lesson.getStartTime()).add(lesson);
                            if (lastStartTime.equals(lesson.getStartTime())) {
                                multipleLessonAtOnce = true;
                            }
                            lastStartTime = lesson.getStartTime();
                        }
                        if (multipleLessonAtOnce) {
                            for (ArrayList<Timetable.Lesson> listLessons: lessons.values()) {
                                for (Timetable.Lesson lesson: listLessons) {
                                    embedBuilder.addField(Utils.advancedFormat(language.getString("timetable-lesson-title"), new HashMap<String, Object>() {{
                                        put("lesson-number", lesson.getTimeUnitObject().getName());
                                        put("start-time", lesson.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                        put("end-time", lesson.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    }}), Utils.advancedFormat(language.getString("timetable-teachers"), new HashMap<String, Object>() {{
                                        put("teachers", String.join(", ", lesson.getTeachers().getFullNames()));
                                    }}) + "\n" + Utils.advancedFormat(language.getString("timetable-subjects"), new HashMap<String, Object>() {{
                                        put("subjects", String.join(", ", lesson.getSubjects().getLongNames()));
                                    }}) + "\n" + Utils.advancedFormat(language.getString("timetable-rooms"), new HashMap<String, Object>() {{
                                        put("rooms", String.join(", ", lesson.getRooms().getLongNames()));
                                    }}), listLessons.size() > 1);
                                }
                            }
                        } else {
                            /*int halfSize = (int) Math.ceil((lessons.values().size() / 2));
                            for (int i = 0; i <= halfSize; i++) {
                                int j = i + 1;
                                Timetable.Lesson lesson = ((ArrayList<Timetable.Lesson>) lessons.values().toArray()[i]).get(0);
                                Timetable.Lesson[] leftRightLessons;

                                if (j + halfSize > lessons.values().size() - 1) {
                                    leftRightLessons = new Timetable.Lesson[]{lesson};
                                } else {
                                    leftRightLessons = new Timetable.Lesson[]{lesson, ((ArrayList<Timetable.Lesson>) lessons.values().toArray()[j + halfSize]).get(0)};
                                }

                                for (Timetable.Lesson lesson1: leftRightLessons) {
                                    embedBuilder.addField(Utils.advancedFormat(language.getString("timetable-lesson-title"), new HashMap<String, Object>() {{
                                        put("lesson-number", lesson1.getTimeUnitObject().getName());
                                        put("start-time", lesson1.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                        put("end-time", lesson1.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    }}), Utils.advancedFormat(language.getString("timetable-teachers"), new HashMap<String, Object>() {{
                                        put("teachers", String.join(", ", lesson1.getTeachers().getFullNames()));
                                    }}) + "\n" + Utils.advancedFormat(language.getString("timetable-subjects"), new HashMap<String, Object>() {{
                                        put("subjects", String.join(", ", lesson1.getSubjects().getLongNames()));
                                    }}) + "\n" + Utils.advancedFormat(language.getString("timetable-rooms"), new HashMap<String, Object>() {{
                                        put("rooms", String.join(", ", lesson1.getRooms().getLongNames()));
                                    }}), true);
                                }

                                embedBuilder.addBlankField(true);
                            }*/
                            for (ArrayList<Timetable.Lesson> listLesson: lessons.values()) {
                                Timetable.Lesson lesson = listLesson.get(0);
                                embedBuilder.addField(Utils.advancedFormat(language.getString("timetable-lesson-title"), new HashMap<String, Object>() {{
                                    put("lesson-number", lesson.getTimeUnitObject().getName());
                                    put("start-time", lesson.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("end-time", lesson.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                }}), Utils.advancedFormat(language.getString("timetable-teachers"), new HashMap<String, Object>() {{
                                    put("teachers", String.join(", ", lesson.getTeachers().getFullNames()));
                                }}) + "\n" + Utils.advancedFormat(language.getString("timetable-subjects"), new HashMap<String, Object>() {{
                                    put("subjects", String.join(", ", lesson.getSubjects().getLongNames()));
                                }}) + "\n" + Utils.advancedFormat(language.getString("timetable-rooms"), new HashMap<String, Object>() {{
                                    put("rooms", String.join(", ", lesson.getRooms().getLongNames()));
                                }}), true);
                            }
                        }
                        channel.sendMessage(embedBuilder.build()).queue();
                    } catch (IOException e) {
                        logger.warn(guildId + " ran into an exception while trying to receive a timetable", e);
                        channel.sendMessage("Couldn't get timetable. Try again (later) or contact my author <@650417934073593886>, if the problem won't go away").queue();
                    }
                } else {
                    channel.sendMessage("Wrong number of arguments were given (expected 0 or 1, got " + args.length + "), type `" + data.getPrefix() + "help timetable` for help").queue();
                }
                return;
            case "stats": // `stats` command
                if (args.length == 0) {
                    Data.Stats stats = statsDataConnector.get(guildId);

                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    if (guildName.trim().endsWith("s")) {
                        embedBuilder.setTitle(guild.getName() + " untis status");
                    } else {
                        embedBuilder.setTitle(guild.getName() + "'s untis status");
                    }

                    ArrayList<String> mostMissedTeachers = new ArrayList<>();
                    short missedLessons = 0;
                    for (Map.Entry<String, Short> entry : stats.getAbsentTeachers().entrySet()) {
                        if (entry.getValue() > missedLessons) {
                            mostMissedTeachers.clear();
                            mostMissedTeachers.add(entry.getKey());
                            missedLessons = entry.getValue();
                        } else if (entry.getValue() == missedLessons) {
                            mostMissedTeachers.add(entry.getKey());
                        }
                    }
                    String mostMissedTeachersText;
                    if (missedLessons == 0) {
                        mostMissedTeachersText = "n/a";
                    } else {
                        mostMissedTeachersText = String.join(", ", mostMissedTeachers) + " - " + missedLessons + " missed lessons";
                    }

                    String timetableChecking;
                    if (data.isCheckActive()) {
                        timetableChecking = "\uD83D\uDFE2 Active";
                        embedBuilder.setColor(Color.GREEN);
                    } else {
                        timetableChecking = "\uD83D\uDD34 Inactive";
                        embedBuilder.setFooter("To start timetable checking, type `" + data.getPrefix() + "set-data <username> <password> <loginpage url>` - type `" + data.getPrefix() + "help` for more details");
                        embedBuilder.setColor(Color.RED);
                    }
                    embedBuilder.addField("Timetable checking", timetableChecking, true);
                    //embedBuilder.addField("Checking interval", data.getSleepTime() / 60000 + " minutes", true);
                    embedBuilder.addField("Total timetable requests", String.valueOf(stats.getTotalRequests()), true);
                    embedBuilder.addField("Total lessons checked", String.valueOf(stats.getTotalLessons()), true);
                    embedBuilder.addField("Total weeks checked", String.valueOf((int) (Math.floor((float) stats.getTotalDays() / 7))), true);
                    embedBuilder.addField("Total cancelled lessons", String.valueOf(stats.getTotalCancelledLessons()), true);
                    embedBuilder.addField("Total moved lessons", String.valueOf(stats.getTotalMovedLessons()), true);
                    embedBuilder.addField("Average cancelled lessons per week", String.valueOf(stats.getAverageCancelledLessonsPerWeek()), true);
                    embedBuilder.addField("Average moved lessons per week", String.valueOf(stats.getAverageMovedLessonsPerWeek()), true);
                    embedBuilder.addField("Most missed teacher", mostMissedTeachersText, false);

                    channel.sendMessage(embedBuilder.build()).queue();
                } else {
                    channel.sendMessage("Wrong number of arguments were given (expected 0, got " + args.length + "), type `" + data.getPrefix() + "help stats` for help").queue();
                }
                return;
        }
        if (permission) {
            switch (command) {
                case "channel": // `channel` command
                    if (args.length == 0) {
                        guildDataConnector.update(guild.getIdLong(), null, null, null, null, null, null, channel.getIdLong(), null, null, null, null);
                        logger.info(guildName + " set a new channel to send the timetable changes to");
                        channel.sendMessage("This channel is now set as the channel where I send the timetable changes in").queue();
                    } else {
                        channel.sendMessage("Wrong number of arguments were given (expected 0, got " + args.length + "), type `" + data.getPrefix() + "help channel` for help").queue();
                    }
                    break;
                case "clear": // `clear` command
                    if (args.length == 0) {
                        guildDataConnector.update(guild.getIdLong(), null, "", "", "", "", (short) 0, null, null, null, false, null);
                        logger.info(guildName + " cleared their data");
                        channel.sendMessage("Cleared untis data and stopped timetable listening").queue();
                    } else {
                        channel.sendMessage("Wrong number of arguments were given (expected 0, got " + args.length + "), type `" + data.getPrefix() + "help clear` for help").queue();
                    }
                    break;
                case "data": // `data <username> <password> <server> <school name>` command
                    if (args.length >= 3 && args.length <= 4) {
                        if (dataUpdated.getOrDefault(guildId, LocalDateTime.MIN).plusMinutes(1).isAfter(LocalDateTime.now())) {
                            // this gives the server a little decay time and prevents additional load (because of the untis data encryption) caused by spamming
                            channel.sendMessage("The data was changed recently, try again in about one minute").queue();
                        } else {
                            dataUpdated.put(guildId, LocalDateTime.now());
                            String schoolName;
                            String className;
                            try {
                                schoolName = new URL(args[2]).getQuery().split("=")[1];
                            } catch (MalformedURLException | ArrayIndexOutOfBoundsException e) {
                                channel.sendMessage("The given login data is invalid").queue();
                                return;
                            }
                            String server = args[2].replace("https://", "").replace("http://", "");
                            server = "https://" + server.substring(0, server.indexOf("/"));
                            short klasseId;
                            try {
                                channel.sendMessage("Verifying data...").queue();
                                Session session = Session.login(args[0], args[1], server, schoolName);
                                if (args.length == 3) {
                                    klasseId = (short) session.getInfos().getKlasseId();
                                    className = session.getKlassen().findById(klasseId).getName();
                                } else {
                                    try {
                                        Klassen.KlasseObject klasse = session.getKlassen().findByName(args[3]);
                                        klasseId = (short) klasse.getId();
                                        className = klasse.getName();
                                    } catch (NullPointerException e) {
                                        channel.sendMessage("❌ Cannot find the given class").queue();
                                        return;
                                    }
                                }
                                allUntisSessions.putIfAbsent(guildId, session);
                            } catch (IOException e) {
                                channel.sendMessage("❌ The given login data is invalid").queue();
                                return;
                            }

                            if (data.getChannelId() == null) {
                                guildDataConnector.update(guildId, null, args[0], args[1], server, schoolName, klasseId, channel.getIdLong(), null, null, true, null);
                            } else {
                                guildDataConnector.update(guildId, null, args[0], args[1], server, schoolName, klasseId, null, null, null, true, null);
                            }

                            if (data.isCheckActive()) {
                                Timer timer = allTimetableChecker.get(guildId);
                                allTimetableChecker.remove(guildId);
                                timer.cancel();
                                timer.purge();
                                runTimetableChecker(guild);
                                channel.sendMessage("✅ Updated data and restarted timetable listening for class " + className).queue();
                            } else if (data.getLastChecked() != null) {
                                channel.sendMessage("✅ Updated data. Timetable listening were manually stopped a while ago. To re-enable it, type `" + data.getPrefix() + "start`").queue();
                            } else {
                                runTimetableChecker(guild);
                                channel.sendMessage("✅ Timetable listening has been started for class " + className).queue();
                            }
                            logger.info(guildName + " set new data");
                        }
                    } else {
                        channel.sendMessage("Wrong number of arguments were given (expected 3 or 4, got " + args.length + "), type `" + data.getPrefix() + "help data` for help").queue();
                    }
                    break;
                case "language": // `language <language>` command
                    if (args.length == 1) {
                        String language = args[0];

                        if (!languages.has(language)) {
                            channel.sendMessage("The language `" + language + "` is not supported. Type `" + data.getPrefix() + "help` to see all available languages").queue();
                        } else {
                            guildDataConnector.update(guildId, language, null, null, null, null, null, null, null, null, null, null);
                            logger.info(guildName + " set their language to " + language);
                            channel.sendMessage("Updated language to `" + language + "`").queue();
                        }
                    } else {
                        channel.sendMessage("Wrong number of arguments were given (expected 1, got " + args.length + "), type `" + data.getPrefix() + "help language` for help").queue();
                    }
                    break;
                case "prefix": // `prefix <new prefix>` command
                    if (args.length == 1) {
                        String prefix = args[0];

                        if (prefix.length() == 0 || prefix.length() > 6) {
                            channel.sendMessage("The prefix must be between 1 and 6 characters long").queue();
                        } else {
                            String note = "";
                            if (prefix.contains("'") || prefix.contains("\"")) {
                                channel.sendMessage("Cannot use `'` or `\"` in prefix").queue();
                                return;
                            }
                            if (prefix.length() == 1) {
                                if ("!?$¥§%&@€#|/\\=.:-_+,;*+~<>^°".indexOf(prefix.charAt(0)) == -1) {
                                    note += "\n_Note_: Because the prefix is not in `!?$¥§%&@€#|/\\=.:-_+,;*+~<>^°` you have to call commands with a blank space between it and the prefix";
                                }
                            } else {
                                prefix += " ";
                                note += "\n_Note_: Because the prefix is longer than 1 character you have to call commands with a blank space between it and the prefix";
                            }
                            guildDataConnector.update(guildId, null, null, null, null, null, null, null, prefix, null, null, null);
                            logger.info(guildName + " set their prefix to " + prefix);
                            channel.sendMessage("Updated prefix to `" + prefix + "`" + note).queue();
                        }
                    } else {
                        channel.sendMessage("Wrong number of arguments were given (expected 3, got " + args.length + "), type `" + data.getPrefix() + "help prefix` for help").queue();
                    }
                    break;
                case "start": // `start` command
                    if (args.length == 0) {
                        if (data.isCheckActive()) {
                            channel.sendMessage("Timetable listening already started").queue();
                        } else {
                            runTimetableChecker(guild);
                            logger.info(guildName + " started timetable listening");
                            channel.sendMessage("✅ Timetable listening has been started").queue();
                        }
                    } else {
                        channel.sendMessage("Wrong number of arguments were given (expected 0, got " + args.length + "), type `" + data.getPrefix() + "help start` for help").queue();
                    }
                    break;
                case "stop": // `stop` command
                    if (args.length == 0) {
                        if (data.isCheckActive()) {
                            Timer timer = allTimetableChecker.get(guildId);
                            allTimetableChecker.remove(guildId);
                            timer.cancel();
                            timer.purge();
                            logger.info(guildName + " stopped timetable listening");
                            channel.sendMessage("Stopped timetable listening. Use `" + data.getPrefix() + "start` to re-enable it").queue();
                        } else {
                            channel.sendMessage("Timetable listening is already stopped").queue();
                        }
                    } else {
                        channel.sendMessage("Wrong number of arguments were given (expected 0, got " + args.length + "), type `" + data.getPrefix() + "help stop` for help").queue();
                    }
                    break;
                case "help": // is handled in DiscordCommandListener.onMessageReceived()
                    return;
                default:
                    channel.sendMessage("Unknown command").queue();
            }
        }

    }

    /**
     * Checks the timetable from the given guild and sends an embed if the timetable has changes
     *
     * @param guild guild to send the timetable
     * @since 1.0
     */
    private void runTimetableChecker(Guild guild) {
        long guildId = guild.getIdLong();
        String guildName = guild.getName();
        Timer timer = new Timer();
        Data.Guild data = guildDataConnector.get(guildId);
        TimetableChecker timetableChecker;
        TextChannel textChannel = guild.getTextChannelById(data.getChannelId());
        if (textChannel == null) {
            textChannel = guild.getDefaultChannel();
            if (textChannel != null) {
                guildDataConnector.update(guildId, null, null, null, null, null, null, textChannel.getIdLong(), null, null, null, null);
                textChannel.sendMessage("It seems like, that the channel where I should send the timetable messages in doesn't exists anymore. " +
                        "I'll send the changes now in this channel. " +
                        "If you want that I send these messages into another channel, type `" + data.getPrefix() + "channel` in the channel where I should send the messages in").queue();
            }
        }
        timetableChecker = new TimetableChecker(allUntisSessions.get(guildId), data.getKlasseId());
        timer.scheduleAtFixedRate(new TimerTask() {
            private long latestImportTime = 0;

            private void main() {
                Data.Guild data = guildDataConnector.get(guildId);
                TextChannel textChannel = guild.getTextChannelById(data.getChannelId());
                if (textChannel == null) {
                    textChannel = guild.getDefaultChannel();
                    if (textChannel == null) {
                        return;
                    } else {
                        guildDataConnector.update(guildId, null, null, null, null, null, null, textChannel.getIdLong(), null, null, null, null);
                        textChannel.sendMessage("It seems like, that the channel where I should send the timetable messages in doesn't exists anymore. " +
                                "I'll send the changes now in this channel. " +
                                "If you want that I send these messages into another channel, type `" + data.getPrefix() + "set-channel` in the channel where I should send the messages in").queue();
                    }
                }

                boolean changes = false;
                boolean error = false;
                Data.Stats stats = statsDataConnector.get(guildId);
                String setLanguage = data.getLanguage();
                if (setLanguage == null) {
                    setLanguage = "en";
                }
                JSONObject language = languages.getJSONObject(setLanguage);
                LocalDate now = LocalDate.now();

                int i = 0;
                int daysToCheck = 6;

                try {
                    CheckCallback checkCallback = timetableChecker.check(now);
                    Timetable allLessons = checkCallback.getAllLessons();

                    if (Timetable.sortByStartTime(allLessons).get(allLessons.size() - 1).getEndTime().isBefore(LocalTime.now())) {
                        // checks if all lessons are over, and if so, it stops checking the timetable for today
                        i++;
                        daysToCheck++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ArrayIndexOutOfBoundsException e) {
                    i++;
                    daysToCheck++;
                }

                for (; i <= daysToCheck; i++) {
                    LocalDate localDate = now.plusDays(i);
                    try {
                        CheckCallback checkCallback = timetableChecker.check(localDate);

                        ArrayList<Timetable.Lesson> cancelledLessons = checkCallback.getCancelled();
                        ArrayList<Timetable.Lesson[]> movedLessons = checkCallback.getMoved();
                        ArrayList<Timetable.Lesson> notCancelledLessons = checkCallback.getNotCancelled();
                        ArrayList<Timetable.Lesson[]> notMovedLessons = checkCallback.getNotMoved();

                        if (cancelledLessons.size() != 0 || movedLessons.size() != 0 || notCancelledLessons.size() != 0 || notMovedLessons.size() != 0) {
                            changes = true;

                            EmbedBuilder embedBuilder = new EmbedBuilder();
                            embedBuilder.setColor(Color.CYAN);
                            embedBuilder.setTitle(Utils.advancedFormat(language.getString("change-title"), new HashMap<String, Object>() {{
                                put("weekday", language.getString(localDate.getDayOfWeek().name().toLowerCase()));
                                put("date", localDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                            }}));

                            for (Timetable.Lesson lesson : cancelledLessons) {
                                TimeUnits.TimeUnitObject timeUnitObject = lesson.getTimeUnitObject();
                                HashMap<String, Object> formatMap = new HashMap<String, Object>() {{
                                    put("lesson-number", timeUnitObject.getName());
                                    put("date", lesson.getDate());
                                    put("start-time", timeUnitObject.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("end-time", timeUnitObject.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("teachers", String.join(", ", lesson.getTeachers().getFullNames()));
                                    put("subjects", String.join(", ", lesson.getSubjects().getLongNames()));
                                    put("rooms", String.join(", ", lesson.getRooms().getLongNames()));
                                }};
                                embedBuilder.addField(Utils.advancedFormat(language.getString("cancelled-title"), formatMap),
                                        Utils.advancedFormat(language.getString("cancelled-body"), formatMap), false);
                            }

                            for (Timetable.Lesson[] lesson : movedLessons) {
                                TimeUnits.TimeUnitObject timeUnitObject = lesson[0].getTimeUnitObject();
                                Timetable.Lesson to = lesson[0];
                                Timetable.Lesson from = lesson[1];
                                HashMap<String, Object> formatMap = new HashMap<String, Object>() {{
                                    put("from-lesson-number", from.getTimeUnitObject().getName());
                                    put("to-lesson-number", to.getTimeUnitObject().getName());
                                    put("date", from.getDate());
                                    put("start-time", timeUnitObject.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("end-time", timeUnitObject.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("teachers", String.join(", ", from.getTeachers().getFullNames()));
                                    put("subjects", String.join(", ", from.getSubjects().getLongNames()));
                                    put("rooms", String.join(", ", from.getRooms().getLongNames()));
                                }};
                                embedBuilder.addField(Utils.advancedFormat(language.getString("moved-title"), formatMap),
                                        Utils.advancedFormat(language.getString("moved-body"), formatMap), false);
                            }

                            for (Timetable.Lesson lesson : notCancelledLessons) {
                                TimeUnits.TimeUnitObject timeUnitObject = lesson.getTimeUnitObject();
                                HashMap<String, Object> formatMap = new HashMap<String, Object>() {{
                                    put("lesson-number", timeUnitObject.getName());
                                    put("date", lesson.getDate());
                                    put("start-time", timeUnitObject.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("end-time", timeUnitObject.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("teachers", String.join(", ", lesson.getTeachers().getFullNames()));
                                    put("subjects", String.join(", ", lesson.getSubjects().getLongNames()));
                                    put("rooms", String.join(", ", lesson.getRooms().getLongNames()));
                                }};
                                embedBuilder.addField(Utils.advancedFormat(language.getString("not-cancelled-title"), formatMap),
                                        Utils.advancedFormat(languages.getString("not-cancelled-body"), formatMap), false);
                            }

                            for (Timetable.Lesson[] lesson : notMovedLessons) {
                                TimeUnits.TimeUnitObject timeUnitObject = lesson[0].getTimeUnitObject();
                                Timetable.Lesson from = lesson[0];
                                Timetable.Lesson to = lesson[1];
                                HashMap<String, Object> formatMap = new HashMap<String, Object>() {{
                                    put("from-lesson-number", from.getTimeUnitObject().getName());
                                    put("to-lesson-number", to.getTimeUnitObject().getName());
                                    put("date", from.getDate());
                                    put("start-time", timeUnitObject.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("end-time", timeUnitObject.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    put("teachers", String.join(", ", from.getTeachers().getFullNames()));
                                    put("subjects", String.join(", ", from.getSubjects().getLongNames()));
                                    put("rooms", String.join(", ", from.getRooms().getLongNames()));
                                }};
                                embedBuilder.addField(Utils.advancedFormat(language.getString("not-moved-title"), formatMap),
                                        Utils.advancedFormat(language.getString("not-moved-body"), formatMap), false);
                            }
                            if (!embedBuilder.getFields().isEmpty()) {
                                textChannel.sendMessage(embedBuilder.build()).queue();
                            }

                            LocalDate lastChecked = guildDataConnector.get(guildId).getLastChecked();
                            Short totalDays = stats.getTotalDays();
                            int totalLessons = stats.getTotalLessons();

                            if (lastChecked == null || lastChecked.isBefore(now.plusDays(i))) {
                                totalDays++;
                                totalLessons += checkCallback.getAllLessons().size();
                                guildDataConnector.update(guildId, null, null, null, null, null, null, null, null, null, null, now.plusDays(i));
                            }
                            short totalCancelledLessons = (short) (stats.getTotalCancelledLessons() + cancelledLessons.size() - notCancelledLessons.size());
                            short totalMovedLessons = (short) (stats.getTotalMovedLessons() + movedLessons.size() - notMovedLessons.size());

                            statsDataConnector.update(guildId, stats.getTotalRequests() + 1, totalDays, totalLessons, totalCancelledLessons, totalMovedLessons,
                                    (float) Utils.round((float) totalCancelledLessons / totalLessons, 3) * 5,
                                    (float) Utils.round((float) totalMovedLessons / totalLessons, 3) * 5);

                            for (Timetable.Lesson lesson : checkCallback.getCancelled()) {
                                HashMap<String, Short> teachers = stats.getAbsentTeachers();
                                for (Teachers.TeacherObject teacher : lesson.getTeachers()) {
                                    String name = teacher.getFullName();
                                    statsDataConnector.updateAbsentTeachers(guildId, name, (short) (teachers.getOrDefault(name, (short) 0) + 1));
                                }
                            }
                            for (Timetable.Lesson lesson : checkCallback.getNotCancelled()) {
                                HashMap<String, Short> teachers = stats.getAbsentTeachers();
                                for (Teachers.TeacherObject teacher : lesson.getTeachers()) {
                                    String name = teacher.getFullName();
                                    statsDataConnector.updateAbsentTeachers(guildId, name, (short) (teachers.getOrDefault(name, (short) 0) - 1));
                                }
                            }
                            stats = statsDataConnector.get(guildId);

                            if (error) {
                                error = false;
                            }
                        }
                    } catch (Exception e) {
                        logger.warn(guildName + " ran into an exception while trying to check the timetable for the " + localDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), e);
                        if (!error) {
                            textChannel.sendMessage("An error occurred while trying to check the timetable. " +
                                    "You can try to re-set your data or trying to contact my author <@650417934073593886> (:3) if the problem won't go away").queue();
                            error = true;
                        }
                        try {
                            Thread.sleep((i + 1) * 5000);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
                String changesString = "no changes";

                if (changes) {
                    changesString = "changes";
                }

                logger.info("Checked timetable for " + guildName + " - " + changesString);
            }

            @Override
            public void run() {
                Thread.currentThread().setName(guildName + "(" + guildId + ")");
                try {
                    Session session = timetableChecker.getSession();
                    session.reconnect();
                    long sessionLatestImportTime = session.getLatestImportTime().getLatestImportTime();
                    if (latestImportTime < sessionLatestImportTime) {
                        latestImportTime = sessionLatestImportTime;
                        main();
                    } else {
                        try {
                            Main.getConnection().createStatement().execute("SELECT * FROM Guilds WHERE GUILDID = 0");
                            // just execute this so that the connect won't have a timeout
                        } catch (SQLException ignore) {
                        }
                    }
                } catch (IOException e) {
                    logger.info("Running main through IOException", e);
                    main();
                }
            }
        }, 0, data.getSleepTime());
        allTimetableChecker.put(guildId, timer);
        logger.info(guildName + " started timetable listening");
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        Thread t = new Thread(() -> {
            long guildId = event.getGuild().getIdLong();
            Data.Guild data = guildDataConnector.get(guildId);
            try {
                if (event.getAuthor().isBot() || event.getAuthor().isFake() || !event.getMessage().getContentDisplay().startsWith(data.getPrefix())) {
                    return;
                }
            } catch (StringIndexOutOfBoundsException e) {
                // if (for example) a picture is sent, the bot checks for the first letter from the message an a because a picture has no letters, this error gets thrown
                return;
            }
            String userInput = event.getMessage().getContentDisplay().substring(data.getPrefix().length()).trim().replaceAll(" +", " ");
            String userInputLow = userInput.toLowerCase();

            String[] splitCommand = userInputLow.split(" ");
            String command = splitCommand[0];
            String[] args = Arrays.copyOfRange(splitCommand, 1, splitCommand.length);
            try {
                runCommand(event.getGuild(), event.getChannel(), event.getMember().getPermissions().contains(Permission.ADMINISTRATOR), command, args);
            } catch (NullPointerException ignore) {
            }
        });
        t.setName("Guild message handler");
        t.start();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) { // only for the `help` command
        Thread t = new Thread(() -> {
            if (event.getAuthor().isBot()) {
                return;
            }

            String message = event.getMessage().getContentDisplay().trim().toLowerCase();
            MessageChannel channel = event.getChannel();

            String prefix;
            if (message.contains("help")) { // `help` command
                if (event.isFromGuild()) {
                    prefix = guildDataConnector.get(event.getGuild().getIdLong()).getPrefix();
                    if (!event.getMessage().getContentDisplay().startsWith(prefix + "help")) {
                        System.out.println("sss");
                        return;
                    }
                } else if (message.equals("help") || message.startsWith("help ")) {
                    prefix = "";
                } else {
                    return;
                }
            } else {
                return;
            }

            String[] splitMessage = message.substring(prefix.length()).split(" ");
            String[] args = Arrays.copyOfRange(splitMessage, 1, splitMessage.length);
            String help = "Use `" + prefix + "help <command>` to get help / information about a command.\n\n" +
                    "All available commands are:\n" +
                    "`channel` `clear` `data` `help` `language` `prefix` `stats` `start` `stop` `timetable`";
            if (args.length > 1) {
                channel.sendMessage("Wrong number of arguments are given (expected 0 or 1, got " + splitMessage.length + "). " + help).queue();
            } else if (args.length == 0) {
                channel.sendMessage(help).queue();
            } else {
                String title;
                String description;
                String example;
                String default_ = null;
                switch (args[0]) {
                    case "channel":
                        title = "`channel` command";
                        description = "In the channel where this command is entered, the bot shows the timetable changes";
                        example = "`channel`";
                        break;
                    case "clear":
                        title = "`clear` command";
                        description = "Clears the given untis data, given from the `data` command";
                        example = "`clear`";
                        break;
                    case "data":
                        title = "`data <username> <password> <login page url>` command";
                        description = "Sets the data with which the bot logs in to untis and checks for timetable changes. The data is stored encrypted on the server.\n" +
                                "`username` and `password` are the normal untis login data with which one also logs in to the untis website / app. To gain the login page url you have to go to webuntis.com, type in your school and choose it.\n" +
                                "Then you will be redirected to the untis login page, The url of this page is the login page url, for example `https://example.webuntis.com/WebUntis/?school=myschool#/basic/main`.\n" +
                                "`class name` is just the name of the class you want to check (eg. `12AB`). If `class name` is not specified, the bot tries to get the default class which is assigned to the given account.";
                        example = "`data myname secure https://example.webuntis.com/WebUntis/?school=example#/basic/main 12AB`";
                        default_ = "`en`";
                        break;
                    case "help":
                        title = "`help <command>` command";
                        description = "Displays help to a given command";
                        example = "`help data`";
                        break;
                    case "language":
                        title = "`language <language>` command";
                        description = "Changes the language in which the timetable information are displayed. Currently only 'de' (german) and 'en' (english) are supported";
                        example = "`language de`";
                        default_ = "`en`";
                        break;
                    case "prefix":
                        title = "`prefix <new prefix>` command";
                        description = "Changes the prefix with which commands are called";
                        example = "`prefix $`";
                        default_ = "`!untis `";
                        break;
                    case "stats":
                        title = "`stats` command";
                        description = "Displays a message with some stats (total cancelled lessons, etc.)";
                        example = "`stats`";
                        break;
                    case "start":
                        title = "`start` command";
                        description = "Starts the stopped timetable listener. Only works if data was set with the `data` command";
                        example = "`start`";
                        break;
                    case "stop":
                        title = "`stop` command";
                        description = "Stops timetable listening. Only works if data was set with the `data` command";
                        example = "`stop`";
                        break;
                    case "timetable":
                        title = "`timetable [date] [class name]` command";
                        description = "Displays the timetable for a specific date. As `date` you can use 3 formats." +
                                "1: Only the day (`12`); 2. Day and month (`13.04`); 3. Day, month and year (`31.12.2020`)." +
                                "Only works if data was set with the `data` command. If no date is given, the timetable for the current date is displayed." +
                                "As `class name` you can use any class from your school. If class is not given, the class which was assigned in the `data` command is used";
                        example = "`timetable 11.11`";
                        break;
                    default:
                        channel.sendMessage("Unknown command was given. " + help).queue();
                        return;
                }
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setColor(Color.CYAN);
                embedBuilder.setTitle(title);
                embedBuilder.addField("Description", description, false);
                embedBuilder.addField("Example", example, false);
                if (default_ != null) {
                    embedBuilder.addField("Default", default_, false);
                }
                embedBuilder.setFooter("`<>` = required; `[]` = optional");
                channel.sendMessage(embedBuilder.build()).queue();
            }
        });
        t.setName("Message handler");
        t.start();
    }

    @Override
    public void onReady(ReadyEvent event) {
        ArrayList<Long> allGuilds = new ArrayList<>();
        for (Guild guild : event.getJDA().getGuilds()) {
            long guildId = guild.getIdLong();
            if (!guildDataConnector.has(guildId)) {
                guildDataConnector.add(guildId);
            }
            if (!statsDataConnector.has(guildId)) {
                statsDataConnector.add(guildId);
            }

            if (guildDataConnector.get(guildId).isCheckActive()) {
                Data.Guild data = guildDataConnector.get(guildId);
                try {
                    allUntisSessions.put(guildId, Session.login(data.getUsername(), data.getPassword(), data.getServer(), data.getSchool()));
                    runTimetableChecker(guild);
                } catch (IOException e) {
                    logger.error("Error for guild " + guild.getName() + " (" + guildId + ") while setting up untis session", e);
                }
            }

            allGuilds.add(guildId);
        }
        for (Data.Guild data : guildDataConnector.getAll()) {
            if (!allGuilds.contains(data.getGuildId())) {
                guildDataConnector.remove(data.getGuildId());
                statsDataConnector.remove(data.getGuildId());
            }
        }
        logger.info("Bot is ready | Total guilds: " + guildDataConnector.getAll().size());
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        long guildId = guild.getIdLong();

        if (!guildDataConnector.has(guildId)) {
            guildDataConnector.add(guildId);
        }
        if (!statsDataConnector.has(guildId)) {
            statsDataConnector.add(guildId);
        }
        logger.info("Joined new guild - Name: " + event.getGuild().getName() + " | Total guilds: " + guildDataConnector.getAll().size());
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        long guildId = event.getGuild().getIdLong();
        guildDataConnector.remove(guildId);
        statsDataConnector.remove(guildId);

        logger.info("Left guild - Name: " + event.getGuild().getName() + " | Total guilds: " + guildDataConnector.getAll().size());
    }
}
