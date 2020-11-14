package org.bytedream.untisbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class Utils {

    /**
     * An alternative way to format a string
     *
     * @param stringToFormat string that should be formatted
     * @param args           args to format the string
     * @return the formatted string
     * @since 1.0
     */
    public static String advancedFormat(String stringToFormat, Map<String, Object> args) {
        String formattedString = stringToFormat;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            formattedString = formattedString.replace("{" + entry.getKey() + "}", entry.getValue().toString());
        }
        return formattedString;
    }

    /**
     * Creates a new logger
     *
     * @return the logger
     * @since 1.0
     */
    public static Logger createLogger() {
        return LoggerFactory.getLogger("root");
    }

    /**
     * Checks a given ip for its validity
     *
     * @param ip ip to check
     * @return if the ip is valid
     * @since 1.0
     */
    public static boolean isIPValid(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4 || ip.startsWith(".") || ip.endsWith(".")) {
            return false;
        }

        for (String s : parts) {
            try {
                int i = Integer.parseInt(s);
                if (i < 0 || i > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    /**
     * Rounds numbers to a given decimal place
     *
     * @param value         number to round
     * @param decimalPoints decimal places to round
     * @return the rounded number
     * @since 1.0
     */
    public static double round(double value, int decimalPoints) {
        double d = Math.pow(10, decimalPoints);
        return Math.rint(value * d) / d;
    }

}
