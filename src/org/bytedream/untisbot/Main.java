/**
 * @author ByteDream
 * @version 1.1
 */

package org.bytedream.untisbot;

import ch.qos.logback.classic.Logger;
import org.bytedream.untisbot.data.StoreType;
import org.bytedream.untisbot.discord.Discord;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Main class
 */
public class Main {

    public static final String version = "1.1";

    private static Logger logger;
    private static Connection connection;

    public static void main(String[] args) throws ClassNotFoundException, SQLException, LoginException {
        String os = System.getProperty("os.name").toLowerCase();
        File logFile;
        if (os.contains("linux") || os.contains("unix")) {
            logFile = new File("/var/log/untis.log");
        } else {
            logFile = new File("untis.log");
        }
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.setProperty("LOG_FILE", logFile.getAbsolutePath());
        logger = (Logger) LoggerFactory.getLogger("Untis");
        Discord discord;
        String token = null;
        StoreType storeType = StoreType.MEMORY;
        String dataEncryptPassword = "password";
        String user = "root";
        String password = "";
        String databaseIP = "127.0.0.1";
        String languageFile = "";
        int databasePort = 3306;

        String argsFile = Arrays.stream(args).filter(s -> s.trim().toLowerCase().startsWith("file=")).findAny().orElse(null);

        if (argsFile != null) {
            FileInputStream configReader;
            try {
                configReader = new FileInputStream(argsFile.substring(5));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }

            HashSet<String> argsAsSet = new HashSet<>(Arrays.asList(args));
            JSONTokener jsonTokener = new JSONTokener(configReader);
            JSONObject jsonObject = new JSONObject(jsonTokener);

            for (String s : jsonObject.keySet()) {
                argsAsSet.add(s + "=" + jsonObject.getString(s));
            }

            args = argsAsSet.toArray(new String[0]);
        }

        for (String arg : args) {
            try {
                String[] realArgs = arg.trim().split("=");
                String realValue = realArgs[1].trim();

                switch (realArgs[0].trim().toLowerCase()) {
                    case "token":
                        token = realValue;
                        break;
                    case "user":
                        user = realValue;
                        logger.info("Set custom database user");
                        break;
                    case "password":
                        password = realValue;
                        logger.info("Set custom database password");
                        break;
                    case "ip":
                        if (!Utils.isIPValid(realValue)) {
                            System.err.println("IP is not valid");
                            return;
                        } else {
                            databaseIP = realValue;
                            logger.info("Set custom database ip");
                        }
                        break;
                    case "port":
                        try {
                            databasePort = Integer.parseInt(realValue);
                            logger.info("Set custom database port");
                        } catch (NumberFormatException e) {
                            System.err.println(realValue + " is not a number");
                            return;
                        }
                    case "encrypt":
                        dataEncryptPassword = realValue;
                        logger.info("Set custom database encrypt password");
                        break;
                    case "lng":
                        File file = new File(realValue);
                        if (!file.exists()) {
                            System.err.println("The file '" + realValue + "' doesn't exists");
                            return;
                        }
                        if (!file.isFile()) {
                            System.err.println("'" + realValue + "' must be a file");
                            return;
                        }
                        languageFile = realValue;
                        logger.info("Set custom language file");
                }
            } catch (ArrayIndexOutOfBoundsException ignore) {
                if (arg.trim().equalsIgnoreCase("mariadb")) {
                    storeType = StoreType.MARIADB;
                    logger.info("Using mariadb for data storage");
                }
            }
        }

        if (token == null) {
            System.err.println("Token is missing. Run me again and use your discord bot token as argument (e.g. token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz)");
            return;
        }

        if (storeType == StoreType.MARIADB) {
            Class.forName("org.mariadb.jdbc.Driver");
            String finalDatabaseIP = databaseIP;
            int finalDatabasePort = databasePort;
            String finalUser = user;
            String finalPassword = password;
            connection = DriverManager.getConnection(Utils.advancedFormat("jdbc:mariadb://{databaseIP}:{databasePort}/Untis?user={user}&password={password}", new HashMap<String, Object>() {{
                put("databaseIP", finalDatabaseIP);
                put("databasePort", finalDatabasePort);
                put("user", finalUser);
                put("password", finalPassword);
            }}));
            logger.info("Connected to mariadb");
        }

        InputStream languageFileReader;
        if (languageFile.isEmpty()) {
            languageFileReader = Main.class.getResourceAsStream("language.json");
            if (languageFileReader == null) {
                System.err.println("Cannot load internal language file");
                return;
            }
        } else {
            try {
                languageFileReader = new FileInputStream(languageFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }

        JSONTokener jsonTokener = new JSONTokener(languageFileReader);

        discord = new Discord(token, storeType, dataEncryptPassword, new JSONObject(jsonTokener));
        discord.start();
        logger.info("Started bot");

        //https://discord.com/api/oauth2/authorize?client_id=768841979433451520&permissions=268437504&scope=bot
    }

    /**
     * Returns the logger
     *
     * @return the logger
     * @since 1.0
     */
    public static Logger getLogger() {
        return logger;
    }

    /**
     * Returns the database connection
     *
     * @return the database connection
     * @since 1.0
     */
    public static Connection getConnection() {
        return connection;
    }

}
