FROM yobasystems/alpine-mariadb:latest

ENV token ""
ENV password "toor"
ENV encrypt ""

ENV MYSQL_DATABASE "Untis"
ENV MYSQL_ROOT_PASSWORD $password

RUN mkdir /untisbot-discord/ && \
    mkdir /untisbot-discord/lib && \
    mkdir /untisbot-discord/out && \
    mkdir /untisbot-discord/src

RUN apk add --no-cache openjdk8 curl && \
    rm -f /var/cache/apk/*

RUN wget -O /untisbot-discord/lib/logback-core.jar https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar && \
    wget -O /untisbot-discord/lib/logback-classic.jar https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar && \
    wget -O /untisbot-discord/lib/mariadb-java-client.jar https://downloads.mariadb.com/Connectors/java/connector-java-2.7.0/mariadb-java-client-2.7.0.jar && \
    wget -O /untisbot-discord/lib/untis4j.jar $(curl -s https://api.github.com/repos/ByteDream/untis4j/releases/latest | grep "browser_download_url" | grep "withDependencies.jar" | cut -d '"' -f 4) && \
    wget -O /untisbot-discord/lib/JDA.jar $(curl -s https://api.github.com/repos/DV8FromTheWorld/JDA/releases/latest | grep "browser_download_url" | grep "withDependencies-min.jar" | cut -d '"' -f 4)

ADD dockerfiles/run.sh /untisbot-discord/
ADD dockerfiles/database.sql /untisbot-discord/
ADD src/ /untisbot-discord/src

EXPOSE 3306

VOLUME ["/var/lib/mysql"]

ENTRYPOINT ["/untisbot-discord/run.sh"]