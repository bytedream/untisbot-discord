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
import org.bytedream.untis4j.LoginException;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapter to handle all events
 *
 * @version 1.0
 * @since 1.0
 */
public class DiscordCommandListener extends ListenerAdapter {

    private final DataConnector.Guild guildDataConnector;
    private final DataConnector.Stats statsDataConnector;
    private final JSONObject languages;

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
     * Checks the timetable from the given guild and sends an embed if the timetable has changes
     *
     * @param guild guild to send the timetable
     * @since 1.0
     */
    public void runTimetableChecker(Guild guild) {
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
                textChannel.sendMessage("It seems like, that the channel where I should send the timetable messages in doesn't exists anymore." +
                        "I'll send the changes now in this channel." +
                        "If you want that I send these messages into another channel, type `" + data.getPrefix() + "channel` in the channel where I should send the messages in").queue();
            }
        }
        try {
            timetableChecker = new TimetableChecker(data.getUsername(), data.getPassword(), data.getServer(), data.getSchool(), data.getKlasseId());
        } catch (LoginException e) {
            e.printStackTrace();
            logger.warn(guildName + " failed to login", e);
            textChannel.sendMessage("Failed to login. Please try to re-set your data").queue();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            logger.warn(guildName + " ran into an exception while trying to setup the timetable checker", e);
            textChannel.sendMessage("An error occurred while trying to setup the timetable checking process." +
                    "You should try to re-set your data or trying to contact my author <@650417934073593886> (:3) if the problem won't go away").queue();
            return;
        }
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
                                "I'll send the changes now in this channel." +
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
                            embedBuilder.setTitle(Utils.advancedFormat(language.getString("title"), new HashMap<String, Object>() {{
                                put("weekday", language.getString(localDate.getDayOfWeek().name().toLowerCase()));
                                put("date", localDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                            }}));

                            for (Timetable.Lesson lesson : cancelledLessons) {
                                TimeUnits.TimeUnitObject timeUnitObject = lesson.getTimeUnitObject();
                                HashMap<String, Object> formatMap = new HashMap<String, Object>() {{
                                    put("lesson-name", timeUnitObject.getName());
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
                                    put("from-lesson-name", from.getTimeUnitObject().getName());
                                    put("to-lesson-name", to.getTimeUnitObject().getName());
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
                                    put("lesson-name", timeUnitObject.getName());
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
                                    put("from-lesson-name", from.getTimeUnitObject().getName());
                                    put("to-lesson-name", to.getTimeUnitObject().getName());
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
                            textChannel.sendMessage("An error occurred while trying to check the timetable." +
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
                    logger.info("Running main through IOException (" + e.getCause() + ")");
                    main();
                }
            }
        }, 0, data.getSleepTime());
        allTimetableChecker.put(guildId, timer);
        logger.info(guildName + " started timetable listening");
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
                runTimetableChecker(guild);
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
            String guildName = event.getGuild().getName();

            Guild guild = event.getGuild();
            String userInput = event.getMessage().getContentDisplay().substring(data.getPrefix().length()).trim().replaceAll(" +", " ");
            String userInputLow = userInput.toLowerCase();

            String[] splitCommand = userInputLow.split(" ");
            String command = splitCommand[0];
            String[] args = Arrays.copyOfRange(splitCommand, 1, splitCommand.length);

            TextChannel channel = event.getChannel();

            try {
                if (command.equals("stats")) {
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
                        channel.sendMessage("Wrong number of arguments were given, type `" + data.getPrefix() + "help` for help").queue();
                    }
                } else if (event.getMember().getPermissions().contains(Permission.ADMINISTRATOR)) {
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
                                        session.logout();
                                    } catch (IOException e) {
                                        channel.sendMessage("❌ The given login data is invalid").queue();
                                        return;
                                    }

                                    boolean isCheckActive = data.isCheckActive();

                                    if (data.getChannelId() == null) {
                                        guildDataConnector.update(guildId, null, args[0], args[1], server, schoolName, klasseId, channel.getIdLong(), null, null, true, null);
                                    } else {
                                        guildDataConnector.update(guildId, null, args[0], args[1], server, schoolName, klasseId, null, null, null, true, null);
                                    }

                                    if (isCheckActive) {
                                        Timer timer = allTimetableChecker.get(guildId);
                                        allTimetableChecker.remove(guildId);
                                        timer.cancel();
                                        timer.purge();
                                        runTimetableChecker(guild);
                                        channel.sendMessage("✅ Updated data and restarted timetable listening for class " + className).queue();
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
                        default:

                    }
                }
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
            EmbedBuilder embedBuilder = new EmbedBuilder();

            String prefix;
            if (message.contains("help")) { // `help` command
                if (event.isFromGuild()) {
                    prefix = guildDataConnector.get(event.getGuild().getIdLong()).getPrefix();
                    embedBuilder.setFooter("Note: Every command must be called with the set prefix ('" + prefix + "')");
                    if (!event.getMessage().getContentDisplay().startsWith(prefix + "help")) {
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
                    "`channel` `clear` `data` `help` `language` `prefix` `stats`";
            if (args.length > 1) {
                channel.sendMessage("Wrong number of arguments are given (expected 0 or 1, got " + splitMessage.length + "). " + help).queue();
            } else if (args.length == 0) {
                channel.sendMessage(help).queue();
            } else {
                String title;
                String description;
                String example;
                String _default = null;
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
                        _default = "`en`";
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
                        _default = "`en`";
                        break;
                    case "prefix":
                        title = "`prefix <new prefix>` command";
                        description = "Changes the prefix with which commands are called";
                        example = "`prefix $`";
                        _default = "`!untis `";
                        break;
                    case "stats":
                        title = "`stats` command";
                        description = "Displays a message with some stats (total cancelled lessons, etc.)";
                        example = "`stats`";
                        break;
                    default:
                        channel.sendMessage("Unknown command was given. " + help).queue();
                        return;
                }
                embedBuilder.setColor(Color.CYAN);
                embedBuilder.setTitle(title);
                embedBuilder.addField("Description", description, false);
                embedBuilder.addField("Example", example, false);
                if (_default != null) {
                    embedBuilder.addField("Default", _default, false);
                }
                embedBuilder.setFooter("`<>` = required; `[]` = optional");
                channel.sendMessage(embedBuilder.build()).queue();
            }
        });
        t.setName("Message handler");
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
