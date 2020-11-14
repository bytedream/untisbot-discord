package org.bytedream.untisbot.data;

import org.bytedream.untisbot.Crypt;
import org.bytedream.untisbot.Main;

import java.security.GeneralSecurityException;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Base class to manage all data
 *
 * @version 1.0
 * @since 1.0
 */
public class DataConnector {

    private final StoreType storeType;
    private final Crypt crypt;

    public DataConnector(StoreType storeType, Crypt crypt) {
        this.storeType = storeType;
        this.crypt = crypt;
    }

    public Guild guildConnector() {
        return new Guild(storeType, crypt);
    }

    public Stats statsConnector() {
        return new Stats(storeType);
    }

    /**
     * Class to manage all the guild data
     *
     * @version 1.0
     * @since 1.0
     */
    public static class Guild {
        private final StoreType storeType;
        private final Crypt crypt;
        private final Map<Long, Data.Guild> memoryData = new HashMap<>();
        private Connection connection;

        /**
         * Initializes the guild data connector and connects to the database if {@code storeType} is database
         *
         * @param storeType type how to store the given untis data {@link StoreType}
         * @param crypt     {@link Crypt} class to en- / decrypt the untis account passwords
         * @since 1.0
         */
        private Guild(StoreType storeType, Crypt crypt) {
            this.storeType = storeType;
            this.crypt = crypt;
            if (storeType == StoreType.MARIADB) {
                connection = Main.getConnection();
            }
        }

        /**
         * Creates a new guild data entry
         *
         * @param guildId guild id of the new entry
         * @since 1.0
         */
        public void add(long guildId) {
            if (storeType == StoreType.MARIADB) {
                try {
                    connection.createStatement().executeUpdate("INSERT INTO Guilds (GUILDID) VALUES (" + guildId + ")");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                Object[] data = new Object[12];
                data[0] = guildId;
                data[1] = null;
                data[2] = null;
                data[3] = null;
                data[4] = null;
                data[5] = null;
                data[6] = null;
                data[7] = null;
                data[8] = "!untis ";
                data[9] = 3600000L;
                data[10] = false;
                data[11] = null;
                memoryData.put(guildId, new Data.Guild(data, crypt));
            }
        }

        /**
         * Returns the guild data from a guild id
         *
         * @param guildId to get the data from
         * @return the guild data
         * @since 1.0
         */
        public Data.Guild get(long guildId) {
            Object[] data = new Object[12];

            if (storeType == StoreType.MARIADB) {
                try {
                    ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM Guilds WHERE GUILDID=" + guildId);

                    while (resultSet.next()) {
                        ResultSetMetaData metaData = resultSet.getMetaData();

                        for (int i = 1; i <= metaData.getColumnCount(); i++) {
                            switch (metaData.getColumnType(i)) {
                                case 5: //small int
                                    data[i - 1] = resultSet.getShort(i);
                                    break;
                                case 91: //date
                                    Date date = resultSet.getDate(i);
                                    if (date != null) {
                                        data[i - 1] = date.toLocalDate();
                                    } else {
                                        data[i - 1] = null;
                                    }
                                    break;
                                default:
                                    data[i - 1] = resultSet.getObject(i);
                            }
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                data = memoryData.get(guildId).getData();
            }
            return new Data.Guild(data, crypt);
        }

        /**
         * Returns all stored guild data
         *
         * @return all stored guild data
         * @since 1.0
         */
        public HashSet<Data.Guild> getAll() {
            HashSet<Data.Guild> allData = new HashSet<>();
            if (storeType == StoreType.MARIADB) {
                try {
                    ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM Guilds");

                    while (resultSet.next()) {
                        Object[] data = new Object[12];
                        int maxColumns = resultSet.getMetaData().getColumnCount();

                        for (int i = 1; i <= maxColumns; i++) {
                            Object object = resultSet.getObject(i);
                            data[i - 1] = object;
                        }

                        allData.add(new Data.Guild(data, crypt));
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                allData.addAll(memoryData.values());
            }
            return allData;
        }

        /**
         * Updates the guild data for a specific guild id
         *
         * @param guildId       guild id from which the data should be updated
         * @param language      new language in which the timetable changes should be displayed
         * @param username      new untis username
         * @param password      new untis password
         * @param server        new untis server
         * @param school        new untis school
         * @param channelId     new channel id in which the timetable changes are sent
         * @param prefix        new command prefix
         * @param sleepTime     new sleep time between every timetable check
         * @param isCheckActive new boolean to say if the timetable should be checked
         * @param lastChecked   new date on which the timetable was last checked
         * @since 1.0
         */
        public void update(long guildId, String language, String username, String password, String server, String school, Short klasseId, Long channelId, String prefix, Long sleepTime, Boolean isCheckActive, LocalDate lastChecked) {
            LinkedHashMap<String, Object> args = new LinkedHashMap<>();

            args.put("GUILDID", guildId);
            args.put("LANGUAGE", language);
            if (username != null) {
                if (username.isEmpty()) {
                    args.put("USERNAME", "NULL");
                } else {
                    try {
                        args.put("USERNAME", crypt.encrypt(username));
                    } catch (GeneralSecurityException ignore) {
                        args.put("USERNAME", null);
                    }
                }
            } else {
                args.put("USERNAME", null);
            }
            if (password != null) {
                if (password.isEmpty()) {
                    args.put("PASSWORD", "NULL");
                } else {
                    try {
                        args.put("PASSWORD", crypt.encrypt(password));
                    } catch (GeneralSecurityException ignore) {
                        args.put("PASSWORD", null);
                    }
                }
            } else {
                args.put("PASSWORD", null);
            }
            if (server != null) {
                if (server.isEmpty()) {
                    args.put("SERVER", "NULL");
                } else {
                    args.put("SERVER", server);
                }
            } else {
                args.put("SERVER", null);
            }
            if (school != null) {
                if (school.isEmpty()) {
                    args.put("SCHOOL", "NULL");
                } else {
                    args.put("SCHOOL", school);
                }
            } else {
                args.put("SCHOOL", null);
            }
            args.put("KLASSEID", klasseId);
            args.put("CHANNELID", channelId);
            args.put("PREFIX", prefix);
            args.put("SLEEPTIME", sleepTime);
            args.put("ISCHECKACTIVE", isCheckActive);
            args.put("LASTCHECKED", lastChecked);

            if (storeType == StoreType.MARIADB) {
                StringBuilder stringBuilder = new StringBuilder("UPDATE Guilds SET ");
                for (Map.Entry<String, Object> entry : args.entrySet()) {
                    Object value = entry.getValue();
                    if (value != null) {
                        if (String.class.isAssignableFrom(value.getClass())) {
                            stringBuilder.append(entry.getKey()).append("='").append((String) value).append("',");
                        } else if (LocalDate.class.isAssignableFrom(value.getClass())) {
                            stringBuilder.append(entry.getKey()).append("='").append(((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE)).append("',");
                        } else {
                            stringBuilder.append(entry.getKey()).append("=").append(value).append(",");
                        }
                    }
                }

                String preFinalQuery = stringBuilder.toString();
                preFinalQuery = preFinalQuery.substring(0, preFinalQuery.length() - 1);

                try {
                    connection.createStatement().executeUpdate(preFinalQuery + " WHERE GUILDID=" + guildId);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                Object[] data = memoryData.get(guildId).getData();
                Iterator<Object> iterator = args.values().iterator();

                int index = 0;
                while (iterator.hasNext()) {
                    Object o = iterator.next();
                    if (o != null) {
                        data[index] = o;
                    }
                    index++;
                }
                memoryData.replace(guildId, new Data.Guild(data, crypt));
            }
        }

        /**
         * Checks if the given guild id exist in the guild data
         *
         * @param guildId to check
         * @return if the guild id exists
         * @since 1.0
         */
        public boolean has(long guildId) {
            if (storeType == StoreType.MARIADB) {
                try {
                    return connection.createStatement().executeQuery("SELECT GUILDID FROM Guilds WHERE GUILDID=" + guildId).first();
                } catch (SQLException e) {
                    e.printStackTrace();
                    return true;
                }
            } else {
                return memoryData.containsKey(guildId);
            }
        }

        /**
         * Removes a guild data entry
         *
         * @param guildId guild id of the entry to be removed
         * @since 1.0
         */
        public void remove(long guildId) {
            if (storeType == StoreType.MARIADB) {
                try {
                    connection.createStatement().executeUpdate("DELETE FROM Guilds WHERE GUILDID=" + guildId);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                memoryData.remove(guildId);
            }
        }
    }

    /**
     * Class to manage all the guild stats
     *
     * @version 1.0
     * @since 1.0
     */
    public static class Stats {
        private final StoreType storeType;
        private final Map<Long, Data.Stats> memoryData = new HashMap<>();
        private Connection connection;

        /**
         * Initializes the stats data connector and connects to the database if {@code storeType} is database
         *
         * @param storeType type how to store the given untis data {@link StoreType}
         * @since 1.0
         */
        private Stats(StoreType storeType) {
            this.storeType = storeType;
            if (storeType == StoreType.MARIADB) {
                connection = Main.getConnection();
            }
        }

        /**
         * Creates a new stats data entry
         *
         * @param guildId guild id of the new entry
         * @since 1.0
         */
        public void add(long guildId) {
            if (storeType == StoreType.MARIADB) {
                try {
                    connection.createStatement().executeUpdate("INSERT INTO Stats (GUILDID) VALUES (" + guildId + ");");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                Object[] data = new Object[10];
                data[0] = guildId;
                data[1] = 0;
                data[2] = 0;
                data[3] = 0;
                data[4] = (short) 0;
                data[5] = (short) 0;
                data[6] = 0f;
                data[7] = 0f;
                data[8] = new HashMap<String, Short>();
                memoryData.put(guildId, new Data.Stats(data));
            }
        }

        /**
         * Returns the stats data from a guild id
         *
         * @param guildId to get the data from
         * @return the stats data
         * @since 1.0
         */
        public Data.Stats get(long guildId) {
            if (storeType == StoreType.MARIADB) {
                Object[] data = new Object[9];
                try {
                    ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM Stats WHERE GUILDID=" + guildId);

                    while (resultSet.next()) {
                        ResultSetMetaData metaData = resultSet.getMetaData();

                        for (int i = 1; i <= metaData.getColumnCount(); i++) {
                            switch (metaData.getColumnType(i)) {
                                case 5: //small int
                                    data[i - 1] = resultSet.getShort(i);
                                    break;
                                case 6: //float
                                    data[i - 1] = resultSet.getFloat(i);
                                    break;
                                default:
                                    data[i - 1] = resultSet.getObject(i);
                            }
                        }
                    }

                    resultSet = connection.createStatement().executeQuery("SELECT * FROM AbsentTeachers WHERE GUILDID=" + guildId);
                    HashMap<String, Short> absentTeachers = new HashMap<>();
                    while (resultSet.next()) {
                        absentTeachers.put(resultSet.getString("TEACHERNAME"), resultSet.getShort("ABSENTLESSONS"));
                    }
                    data[0] = guildId;
                    data[8] = absentTeachers;
                    return new Data.Stats(data);
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                return memoryData.get(guildId);
            }
        }

        /**
         * Updates the stats data for a specific guild id
         *
         * @param guildId                        guild id from which the data should be updated
         * @param totalRequests                  new total timetable requests
         * @param totalDays                      new total days that have been checked
         * @param totalLessons                   new total lessons that have been checked
         * @param totalCancelledLessons          new total cancelled lessons that have been checked
         * @param totalMovedLessons              new total moved lessons that have been checked
         * @param averageCancelledLessonsPerWeek new average cancelled lessons per week
         * @param averageMovedLessonsPerWeek     new average moved lessons per week
         * @since 1.0
         */
        public void update(long guildId, Integer totalRequests, Short totalDays, Integer totalLessons, Short totalCancelledLessons, Short totalMovedLessons, Float averageCancelledLessonsPerWeek, Float averageMovedLessonsPerWeek) {
            LinkedHashMap<String, Object> args = new LinkedHashMap<>();
            args.put("GUILDID", guildId);
            args.put("TOTALREQUESTS", totalRequests);
            args.put("TOTALDAYS", totalDays);
            args.put("TOTALLESSONS", totalLessons);
            args.put("TOTALCANCELLEDLESSONS", totalCancelledLessons);
            args.put("TOTALMOVEDLESSONS", totalMovedLessons);
            args.put("AVERAGECANCELLEDLESSONS", averageCancelledLessonsPerWeek);
            args.put("AVERAGEMOVEDLESSONS", averageMovedLessonsPerWeek);
            if (storeType == StoreType.MARIADB) {
                String[] argsClasses = new String[]{"Long", "Integer", "Short", "Integer", "Short", "Short", "Float", "Float"};

                StringBuilder stringBuilder = new StringBuilder("UPDATE Stats SET ");
                int index = 0;
                for (Map.Entry<String, Object> entry : args.entrySet()) {
                    Object value = entry.getValue();
                    if (value != null) {
                        switch (argsClasses[index]) {
                            case "Float":
                                if (Float.isNaN((Float) value)) {
                                    value = 0f;
                                }
                            case "Integer":
                            case "Short":
                                stringBuilder.append(entry.getKey()).append("=").append(value).append(",");
                                break;
                        }
                    }
                    index++;
                }

                String preFinalQuery = stringBuilder.toString();
                preFinalQuery = preFinalQuery.substring(0, preFinalQuery.length() - 1);

                try {
                    connection.createStatement().executeUpdate(preFinalQuery + " WHERE GUILDID=" + guildId);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                Data.Stats stats = memoryData.get(guildId);
                Object[] data = stats.getData();

                Iterator<Object> iterator = args.values().iterator();

                int index = 0;
                while (iterator.hasNext()) {
                    Object o = iterator.next();
                    if (o != null) {
                        data[index] = o;
                    }
                    index++;
                }

                data[9] = stats.getAbsentTeachers();
                memoryData.replace(guildId, new Data.Stats(data));
            }
        }

        /**
         * Updates the absent teachers data for a specific guild id
         *
         * @param guildId       guild id from which the data should be updated
         * @param teacherName   teacher name that should be updated
         * @param absentLessons new number of lessons where the teacher were absent
         * @since 1.0
         */
        public void updateAbsentTeachers(long guildId, String teacherName, short absentLessons) {
            if (storeType == StoreType.MARIADB) {
                try {
                    if (!connection.createStatement().executeQuery("SELECT GUILDID FROM AbsentTeachers WHERE GUILDID=" + guildId + " AND TEACHERNAME='" + teacherName + "'").first()) {
                        PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO AbsentTeachers (GUILDID, TEACHERNAME, ABSENTLESSONS) VALUES (?, ?, ?)");
                        preparedStatement.setLong(1, guildId);
                        preparedStatement.setString(2, teacherName);
                        preparedStatement.setShort(3, absentLessons);
                        preparedStatement.executeUpdate();
                    } else {
                        PreparedStatement preparedStatement = connection.prepareStatement("UPDATE AbsentTeachers SET ABSENTLESSONS=? WHERE GUILDID=? AND TEACHERNAME=?");
                        preparedStatement.setShort(1, absentLessons);
                        preparedStatement.setLong(2, guildId);
                        preparedStatement.setString(3, teacherName);
                        preparedStatement.executeUpdate();
                    }
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            } else {
                memoryData.get(guildId).getAbsentTeachers().computeIfPresent(teacherName, (s, aShort) -> memoryData.get(guildId).getAbsentTeachers().put(s, aShort));
                memoryData.get(guildId).getAbsentTeachers().putIfAbsent(teacherName, absentLessons);
            }
        }

        /**
         * Checks if the given guild id exist in the stats data
         *
         * @param guildId to check
         * @return if the guild id exists
         * @since 1.0
         */
        public boolean has(long guildId) {
            if (storeType == StoreType.MARIADB) {
                try {
                    return connection.createStatement().executeQuery("SELECT GUILDID FROM Stats WHERE GUILDID=" + guildId).first();
                } catch (SQLException e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                return memoryData.containsKey(guildId);
            }
        }

        /**
         * Removes a guild data entry
         *
         * @param guildId guild id of the entry to be removed
         * @since 1.0
         */
        public void remove(long guildId) {
            if (storeType == StoreType.MARIADB) {
                try {
                    connection.createStatement().executeUpdate("DELETE FROM Stats WHERE GUILDID=" + guildId);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                try {
                    connection.createStatement().executeUpdate("DELETE FROM AbsentTeachers WHERE GUILDID=" + guildId);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                memoryData.remove(guildId);
            }
        }
    }

}
