### UntisBot

**UntisBot** is a java programmed discord bot, which uses the [WebUntis](https://webuntis.com/) timetable software / api to automatically sends messages when the timetable from a given account or class changes.
You can invite the bot right [here](https://github.com/ByteDream/untisbot-discord/releases/tag/v1.0/UntisBot-1.0.jar) or [host it yourself](#Self-hosting).

## Commands

The default prefix for the bot is `!untis `, so you have to call every command with `!untis <command>`.

To see all available commands and get infos about it, simply type `help`

`channel` - In the channel where this command is entered, the bot shows the timetable changes | eg. `channel`.

`clear` - Clears the given untis data, given from the `data` command | eg. `clear`.

`data <username> <password> <login page url> [class name]` - Sets the data with which the bot logs in to untis and checks for timetable changes. The data is stored encrypted on the server.
`username` and `password` are the normal untis login data with which one also logs in to the untis website / app. To gain the login page url you have to go to webuntis.com, type in your school and choose it.
Then you will be redirected to the untis login page, The url of this page is the login page url, for example `https://example.webuntis.com/WebUntis/?school=myschool#/basic/main`.
`class name` is just the name of the class you want to check (eg. `12AB`). If `class name` is not specified, the bot tries to get the default class which is assigned to the given account.

eg. `data myname secure https://example.webuntis.com/WebUntis/?school=example#/basic/main 12AB`.

`help <command>` - Displays help to a given command | eg. `help data`.

`language <language>` - Changes the language in which the timetable information are displayed. Currently only 'de' (german) and 'en' (english) are supported | eg. `language de` | default: `en`.

`prefix <new prefix>` - Changes the prefix with which commands are called | eg. `prefix $` | default: `!untis `.

`stats` - Displays a message with some stats (total cancelled lessons, etc.) | eg. `stats`.

`<>` = required; `[]` = optional

Note: All commands except for `help <command>` and `<stats>` can only be executed by a member with admin rights.

## Self-hosting

If you want to host **UntisBot** on your own server / pc you have the choice between two types of hosting:
 - Run the bot in a [docker container](#Docker)
 - Run it [natively](#Natively) on your machine

## Docker

Download this repository with `git clone https://github.com/ByteDream/untisbot-discord.git` and go into the cloned directory.
Then run `docker build -t untisbot-discord .` to build the docker image and if this is done, type `docker run -d --name untisbot-discord -e token=<your discord token> untisbot-discord` to run it.

Note: You can declare more [environment variables](#Run-options-for-docker-container) besides `token`.
 
## Natively

When you run the bot natively you can choose from 2 types of data storage:
 - [In-memory](#In-memory-storage) (simpler)
 - [Database storage](#MariaDB) (MariaDB)
 
### In-memory storage

In memory data storage is pretty simple: Just download the [jar]() and run it with `java -jar UntisBot-1.0.jar token=<your discord bot token>`.
The simple things have unfortunately also often disadvantages: The user data is only stored as long as the bot is running. If you shut it down, all data will be lost.
If you want to keep the data even after a shutdown, you should use [database storage](#MariaDB).

### MariaDB

**_Note_: This description is only for linux, but the most things should also work on windows**

With MariaDB you can store the data safely in a sql database, and they won't be lost after a shutdown.

If you haven't installed MariaDB, you can follow the instructions from [here](https://linuxize.com/post/how-to-install-mariadb-on-ubuntu-18-04/) (this tutorial is for ubuntu, but it should work with every debian distro).

To set up the database, you have two options to choose from.

##### The short one:
```bash
mysql --user=<user> --password=<password> -e "CREATE DATABASE Untis;" && https://raw.githubusercontent.com/ByteDream/untisbot-discord/master/src/org/bytedream/untisbot/dockerfiles/database.sql | mysql --user=<user> --password=<password> Untis
```
Just copy this and replace `<user>` with the sql user which should manage the database and `<password>` with the user's password.

---

##### And the long one:

First you have to connect you with MariaDB. When you are connected, enter the following commands (without the '>'):
```sql
> CREATE DATABASE Untis;
> USE Untis;
> CREATE TABLE Guilds (GUILDID BIGINT NOT NULL, LANGUAGE TINYTEXT, USERNAME TINYTEXT, PASSWORD TEXT, SERVER TINYTEXT, SCHOOL TINYTEXT, KLASSEID SMALLINT, CHANNELID BIGINT, PREFIX VARCHAR(7) DEFAULT '!untis ' NOT NULL, SLEEPTIME BIGINT DEFAULT 3600000 NOT NULL, ISCHECKACTIVE BOOLEAN DEFAULT FALSE NOT NULL, LASTCHECKED DATE);
> CREATE TABLE Stats (GUILDID BIGINT NOT NULL, TOTALREQUESTS INT DEFAULT 0 NOT NULL, TOTALDAYS SMALLINT DEFAULT 0 NOT NULL, TOTALLESSONS INT DEFAULT 0 NOT NULL, TOTALCANCELLEDLESSONS SMALLINT DEFAULT 0 NOT NULL, TOTALMOVEDLESSONS SMALLINT DEFAULT 0 NOT NULL, AVERAGECANCELLEDLESSONS FLOAT DEFAULT 0 NOT NULL, AVERAGEMOVEDLESSONS FLOAT DEFAULT 0 NOT NULL);
> CREATE TABLE AbsentTeachers (GUILDID BIGINT NOT NULL, TEACHERNAME TINYTEXT NOT NULL, ABSENTLESSONS SMALLINT NOT NULL);
```

---

Now you have set up the database and are ready to go. Download the [jar]() and run it with `java -jar UntisBot-1.0.jar <your discord bot token> mariadb`.

## Run options

The syntax of the following arguments / run option is very simple: `key=value`.

### Run options for docker container

When you start the container you can declare several environment variables:
 - `token` (required!) - The discord bot token
 - `password` (optional) - Password for the given user | default: `toor`
 - `encrypt` (optional) - A password to encrypt the user's untis username and password | default: `password`

(always remember when declaring a new environment variable `-e` must be prefixed)

---

Example: 
 - `docker run -d -e token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz -e password=very_secure untisbot-discord`

### Run options for native hosting

There are several arguments to start the bot with:
 - `token` (required!) - The discord bot token
 - `encrypt` (optional) - A password to encrypt the user's untis username and password | default: `password`
 - `lng` (optional) - Path to a language file | default: `` (uses the [internal](src/org/bytedream/untisbot/language.json) language file)
 
The following arguments are only for MariaDB user:
 - `user` (optional) - The user who should connect to the mariadb database | default: `root`
 - `password` (optional) - Password for the given mariadb user | default: ``
 - `port` (optional) - Port of mariadb | default: `3306`
 - `ip` (optional) - IP address of mariadb | default: `127.0.0.1`
 
If you want to use MariaDB as store type you have to add the argument `mariadb` (without any value).

---

Alternatively, you can write the arguments in a `json` file and load this via `java -jar UntisBot-1.0.jar file=<file where the arguments are in>`

Example: 
```json
{
  "token": "BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz",
  "lng": "lng.json"
}
```

If you use this, you can still declare arguments on the command line, but if they are also in the json file, they will be overwritten.
This might be useful when you run the bot on a server and won't that your token or other args are shown when, for example, [htop](https://github.com/htop-dev/htop/) is running where you can see the cli arguments.

---

In-memory examples:
 - `UntisBot-1.0.jar token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz`
 - `UntisBot-1.0.jar token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz encrypt=super_secure_password lng=/home/user/more_languages.json`
 
MariaDB examples:
 - `UntisBot-1.0.jar mariadb token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz encrypt=super_ultra_secure_password`
 - `UntisBot-1.0.jar mariadb token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz encrypt=super_ultra_secure_password user=untis password=toor`
 
## Dependencies

- Java 8 or higher
- [Discord library](https://github.com/DV8FromTheWorld/JDA) (JDA)
- [Untis library](https://github.com/ByteDream/untis4j) (untis4j)
- [MariaDB client](https://github.com/mariadb-corporation/mariadb-connector-j) (mariadb java client)
- [Logger](https://github.com/qos-ch/logback) (logback-core and logback-classic)

**_Note_: The [UntisBot jar file](https://github.com/ByteDream/untisbot-discord/releases/tag/v1.0/UntisBot-1.0.jar) and the [Dockerfile](Dockerfile) are containing all dependencies.**