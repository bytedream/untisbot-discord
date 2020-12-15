package org.bytedream.untisbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.bytedream.untisbot.Crypt;
import org.bytedream.untisbot.Main;
import org.bytedream.untisbot.data.StoreType;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Base class to start the bot
 *
 * @version 1.0
 * @since 1.0
 */
public class Discord {

    private static JDA jda = null;
    private final JDABuilder jdaBuilder;

    /**
     * Configures the bot to make it ready to launch
     *
     * @param token           bot token
     * @param storeType       type how to store the given untis data {@link StoreType}
     * @param encryptPassword password to encrypt all passwords from the untis accounts
     * @since 1.0
     */
    public Discord(String token, StoreType storeType, String encryptPassword, JSONObject languages) {
        jdaBuilder = JDABuilder.createDefault(token);
        updateRichPresence();
        jdaBuilder.addEventListeners(new DiscordCommandListener(storeType, new Crypt(encryptPassword), languages));
    }

    /**
     * Returns the running jda instance
     *
     * @return the jda instance
     * @since 1.2
     */
    public static JDA getJda() {
        return jda;
    }

    /**
     * Show rich presence if a bot update was released within then last 24 hours
     *
     * @since 1.1
     */
    private void updateRichPresence() {
        if (Main.version.replace(".", "").length() == Main.version.length() - 1) { // only gets executed on major updates
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/ByteDream/untisbot-discord/releases/tags/v" + Main.version).openConnection();
                connection.connect();

                if (connection.getResponseCode() == 200) {
                    JSONTokener jsonTokener = new JSONTokener(new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)));
                    JSONObject releaseInfos = new JSONObject(jsonTokener);
                    String releaseTime = releaseInfos.getString("published_at");
                    LocalDateTime releaseDateTime = LocalDateTime.parse(releaseTime.substring(0, releaseTime.length() - 1));
                    LocalDateTime now = LocalDateTime.now();

                    if (ChronoUnit.DAYS.between(now, releaseDateTime) == 0) {
                        if (jda != null) {
                            jda.getPresence().setActivity(Activity.playing("update " + Main.version + " \uD83C\uDF89"));
                        } else {
                            jdaBuilder.setActivity(Activity.playing("update " + Main.version + " \uD83C\uDF89"));
                        }
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                if (jda != null) {
                                    jda.getPresence().setActivity(null);
                                } else {
                                    jdaBuilder.setActivity(null);
                                }
                            }
                        }, TimeUnit.DAYS.toMillis(1) - ChronoUnit.MILLIS.between(now, releaseDateTime));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Starts the bot
     *
     * @throws LoginException if the given login credentials are invalid
     * @since 1.0
     */
    public void start() throws LoginException {
        jda = jdaBuilder.build();
    }

}
