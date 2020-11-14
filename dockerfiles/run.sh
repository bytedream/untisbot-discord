#!/bin/sh

/scripts/run.sh &

sleep 10

mariadb --user=root --password="$MYSQL_ROOT_PASSWORD" -h 127.0.0.1 Untis < "/untisbot-discord/database.sql"

/usr/lib/jvm/java-1.8-openjdk/bin/javac -cp "/untisbot-discord/lib/*" $(find /untisbot-discord/src/ -name '*.java')

cp -r /untisbot-discord/src/* /untisbot-discord/out/
rm -r $(find /untisbot-discord/out/ -name '*.java')

java -Dfile.encoding=UTF-8 -cp "/untisbot-discord/out:/untisbot-discord/lib/*" org.bytedream.untisbot.Main mariadb token="$token" user=root password="$MYSQL_ROOT_PASSWORD" encrypt="$encrypt"