package org.bytedream.untisbot.discord;

import net.dv8tion.jda.api.JDABuilder;
import org.bytedream.untisbot.Crypt;
import org.bytedream.untisbot.data.StoreType;
import org.json.JSONObject;

import javax.security.auth.login.LoginException;

/**
 * Base class to start the bot
 *
 * @version 1.0
 * @since 1.0
 */
public class Discord {

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
        jdaBuilder.addEventListeners(new DiscordCommandListener(storeType, new Crypt(encryptPassword), languages));
    }

    /**
     * Starts the bot
     *
     * @throws LoginException if the given login credentials are invalid
     * @since 1.0
     */
    public void start() throws LoginException {
        jdaBuilder.build();
    }

}
