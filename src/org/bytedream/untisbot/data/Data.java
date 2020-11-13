package org.bytedream.untisbot.data;

import org.bytedream.untisbot.Crypt;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDate;
import java.util.HashMap;

/**
 * Class to store given guild / user data
 *
 * @version 1.0
 * @since 1.0
 */
public class Data {

    /**
     * Class to store guild data
     *
     * @version 1.0
     * @since 1.0
     */
    public static class Guild {

        private final Crypt crypt;
        private Object[] data;

        public Guild(Object[] data, Crypt crypt) {
            this.data = data;
            this.crypt = crypt;
        }

        public Object[] getData() {
            return data;
        }

        public long getGuildId() {
            return (long) data[0];
        }

        public String getLanguage() {
            return (String) (data[1]);
        }

        public String getUsername() {
            try {
                return crypt.decrypt((String) (data[2]));
            } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException ignore) {
                return null;
            }
        }

        public String getPassword() {
            try {
                return crypt.decrypt((String) (data[3]));
            } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException ignore) {
                return null;
            }
        }

        public String getServer() {
            return (String) data[4];
        }

        public String getSchool() {
            return (String) data[5];
        }

        public Short getKlasseId() {
            return (short) data[6];
        }

        public Long getChannelId() {
            return (Long) data[7];
        }

        public String getPrefix() {
            return (String) data[8];
        }

        public long getSleepTime() {
            return (long) data[9];
        }

        public boolean isCheckActive() {
            return (boolean) data[10];
        }

        public LocalDate getLastChecked() {
            return (LocalDate) data[11];
        }

        protected void update(Object[] data) {
            this.data = data;
        }
    }

    /**
     * Class to store guild stats
     *
     * @version 1.0
     * @since 1.0
     */
    public static class Stats {

        private Object[] data;

        public Stats(Object[] data) {
            this.data = data;
        }

        public Object[] getData() {
            return data;
        }

        public long getGuildId() {
            return (long) data[0];
        }

        public int getTotalRequests() {
            return (int) data[1];
        }

        public short getTotalDays() {
            return (short) data[5];
        }

        public int getTotalLessons() {
            return (int) data[3];
        }

        public short getTotalCancelledLessons() {
            return (short) data[5];
        }

        public short getTotalMovedLessons() {
            return (short) data[5];
        }

        public float getAverageCancelledLessonsPerWeek() {
            return (float) data[6];
        }

        public float getAverageMovedLessonsPerWeek() {
            return (float) data[7];
        }

        public HashMap<String, Short> getAbsentTeachers() {
            return (HashMap<String, Short>) data[8];
        }

        protected void update(Object[] data) {
            this.data = data;
        }

    }

}
