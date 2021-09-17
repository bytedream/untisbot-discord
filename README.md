**In his current state, this bot is a huge mess and it's easier to completely rewrite it than continue developing and fix all the ugly stuff**

### UntisBot

**UntisBot** is a java programmed discord bot, which uses the [WebUntis](https://webuntis.com/) timetable software / api
to receive the timetable for a specific date, automatically send messages when the timetable from a given account or class changes, displays absent teachers, and more!
You can invite the bot right [here](https://discord.com/api/oauth2/authorize?client_id=768841979433451520&permissions=268437504&scope=bot) or [host it yourself](#Self-hosting).

<p align="center">
    <a href="#Commands">Commands</a>
    •
    <a href="#Self-hosting">Self-hosting</a>
    •
    <a href="#Language">Language</a>
    •
    <a href="#Licence">Licence</a>
</p>

## Commands

The default prefix for the bot is `!untis `, so you have to call every command with `!untis <command>`.

To see all available commands and get infos about it, simply type `help`.

`<>` are required arguments, `[]` is optional.

Commands which everyone can execute:

| command | usage | example | default |
| --- | --- | --- | --- |
| `classes` | Displays all classes | `classes` | - |
| `departments` | Displays all departments / buildings | `departments` | - |
| `info` | Displays information about the bot | `info` | - |
| `help [command]` | Displays help to a given command | `help data` | - |
| `holidays` | Displays all holidays with their name, start and end date | `holidays` | - |
| `rooms` | Displays all rooms | `rooms` | - |
| `stats` | Displays a message with some stats (total cancelled lessons, etc.) | `stats` | - |
| `subjects` | Displays all subjects | `subjects` | - |
| `teachers` | Displays all teachers | `teachers` | - |
| `timetable [date] [class name]` | Displays the timetable for a specific date. As `date` you can use 3 formats. 1: Only the day (`12`); 2. Day and month (`13.04`); 3. Day, month and year (`31.12.2020`). Only works if data was set with the `data` command. If no date is given, the timetable for the current date is displayed. As `class name` you can use any class from your school. If class is not given, the class which was assigned in the `data` command is used | `timetable 11.11` | - |


Command which only a member with admin rights can execute:

| command | usage | example | default |
| --- | --- | --- | --- |
| `channel` | In the channel where this command is entered, the bot shows the timetable changes | `channel` | - |
| `clear` | Clears the given untis data, given from the `data` command | `clear` | - |
| `data <username> <password> <login page url> [class name]` | Sets the data with which the bot logs in to untis and checks for timetable changes. The data is stored encrypted on the server. `username` and `password` are the normal untis login data with which one also logs in to the untis website / app. To gain the login page url you have to go to webuntis.com, type in your school and choose it. Then you will be redirected to the untis login page, The url of this page is the login page url, for example `https://example.webuntis.com/WebUntis/?school=myschool#/basic/main`. `class name` is just the name of the class you want to check (eg. `12AB`). As `class name` you can use any class from your school. If it isn't specified, the bot tries to get the default class which is assigned to the given account. | `data myname secure https://example.webuntis.com/WebUntis/?school=example#/basic/main 12AB` | - |
| `language <language>` | Changes the language in which the timetable information are displayed. Currently only `de` (german) and `en` (english) are supported | `language de` | `en` |
| `prefix <new prefix>` | Changes the prefix with which commands are called | `prefix $` | `!untis ` |
| `start` | Starts the stopped timetable listener. Only works if data was set with the `data` command | `start` | - |
| `stop` | Stops timetable listening. Only works if data was set with the `data` command | `stop` | - |

## Self-hosting

The first step before setting up the bot is to generate a bot token on the [discord developer website](https://discord.com/developers/applications).
A more detailed instruction can be found [here](https://www.writebots.com/discord-bot-token/).

If you want to host **UntisBot** on your own server / pc you have the choice between two types of hosting:
 - Run the bot in a [docker container](#Docker)
 - Run it [manually](#Manually) on your machine

## Docker

To run the bot in a docker container, follow the commands below
```bash
git clone https://github.com/ByteDream/untisbot-discord.git
cd untisbot-discord
docker build -t untisbot-discord .
docker run -d --name untisbot-discord -e token=<your discord token> untisbot-discord
```

Note: You can declare more [environment variables](#Run-options-for-docker-container) besides `token`.
 
## Manually

When you run the bot manually you can choose from 2 types of data storage:
 - [In-memory](#In-memory-storage) (simpler)
 - [Database storage](#MariaDB) (MariaDB)
 
### In-memory storage

In memory data storage is pretty simple: Just download the [jar](https://github.com/ByteDream/untisbot-discord/releases/tag/v1.2/UntisBot-1.2.jar) and run it with `java -jar UntisBot-<version>.jar token=<your discord bot token>`.
The simple things have unfortunately also often disadvantages: The user data is only stored as long as the bot is running. If you shut it down, all data will be lost.
If you want to keep the data even after a shutdown, you should use [database storage](#MariaDB).

### MariaDB

**_Note_: This description is only for linux, but the most things should also work on windows**

With MariaDB you can store the data safely in a sql database, and they won't be lost after a shutdown.

If you haven't installed MariaDB, you can follow the instructions from [here](https://linuxize.com/post/how-to-install-mariadb-on-ubuntu-18-04/) (this tutorial is for ubuntu, but it should work with every debian distro).

To set up the database, you have to execute the following command and replace `<user>` with the sql user which should manage the database.
```bash
mysql -u <user> -p -e "CREATE DATABASE Untis; USE Untis; $(wget -qO- https://raw.githubusercontent.com/ByteDream/untisbot-discord/master/files/database.sql)"
```

Now you have set up the database and are ready to go. Download the [jar](https://github.com/ByteDream/untisbot-discord/releases/tag/v1.2/UntisBot-1.2.jar) and run it with `java -jar UntisBot-<version>.jar <your discord bot token> mariadb`.

## Run options

The syntax of the following arguments / run option is very simple: `key=value`.

### Run options for docker container

When you start the container you can declare several environment variables

| variable | description | default | required |
| --- | --- | --- | --- |
| `token` | The discord bot token | - | yes |
| `password` | Password for the given user | `toor` | no |
| `encrypt` | A password to encrypt the user's untis username and password | `password` | no |

(always remember when declaring a new environment variable `-e` must be prefixed)

Example: 
```bash
docker run -d -e token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz -e password=very_secure untisbot-discord
```

### Run options for manual hosting

There are several arguments to start the bot with
 
| variable | description | default | required |
| --- | --- | --- | --- |
| `token` | The discord bot token | - | yes |
| `encrypt` | A password to encrypt the user's untis username and password | `password` | no |
| `lng` | Path to a language file | uses the [internal](src/org/bytedream/untisbot/language.json) language file | no |
 
The following arguments are only for MariaDB user:

| variable | description | default | required |
| --- | --- | --- | --- |
| `user` | The user who should connect to the mariadb database | `root` | no |
| `password` | Password for the given mariadb user |  | no |
| `port` | Database port | `3306` | no |
| `user` | IP address of the database | `127.0.0.1` | no |
 
If you want to use MariaDB as store type you have to add the argument `mariadb` (without any value).

---

Alternatively, you can write the arguments in a `json` file and load this via `java -jar UntisBot-<version>.jar file=<file where the arguments are in>`

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
 - `UntisBot-<version>.jar token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz`
 - `UntisBot-<version>.jar token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz encrypt=super_secure_password lng=/home/user/more_languages.json`
 
MariaDB examples:
 - `UntisBot-<version>.jar mariadb token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz encrypt=super_ultra_secure_password`
 - `UntisBot-<version>.jar mariadb token=BLySFrzvz3tAHtquQevY1FF5W8CT0UMyMNmCSUCbJAPdNAmnnqYVBzaPTkz encrypt=super_ultra_secure_password user=untis password=toor`

## Language

The layout for the language file is available [here](src/org/bytedream/untisbot/language.json).

If you want to add a language which isn't supported you can
- Host the bot [yourself](#Self-hosting) and use the `lng` variable (only compatible if you choose [manually hosting](#Manually))
- Create a new [issue](https://github.com/ByteDream/untisbot-discord/issues/new) or [pull request](https://github.com/ByteDream/untisbot-discord/compare)
  and paste in your json language. The layout has to be like [this](src/org/bytedream/untisbot/language.json).
  Then, after a short check (thx [DeepL](https://www.deepl.com/translator)), I will add it to the repository.

## Dependencies

- Java 8 or higher
- [Discord library](https://github.com/DV8FromTheWorld/JDA) (JDA)
- [Untis library](https://github.com/ByteDream/untis4j) (untis4j)
- [Database client](https://github.com/mariadb-corporation/mariadb-connector-j) (mariadb java client)
- [Logger](https://github.com/qos-ch/logback) (logback-core and logback-classic)

**_Note_: The [UntisBot jar file](https://github.com/ByteDream/untisbot-discord/releases/tag/v1.2/UntisBot-1.2.jar) and the [Dockerfile](Dockerfile) are containing all dependencies.**


## License

This project is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0) - see the [LICENSE](LICENCE) file for more details.
