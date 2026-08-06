# ProxyPunishments — плагин для Velocity и Paper

[![CI](https://github.com/vanfek/proxypunishments-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/vanfek/proxypunishments-plugin/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://openjdk.org/projects/jdk/25/)

*[English version](README.md)*

Система банов для [Velocity](https://papermc.io/software/velocity) и
[Paper](https://papermc.io/software/paper). Состояние банов хранит отдельный сервис
([proxypunishments-service](https://github.com/vanfek/proxypunishments-service)), поэтому один и тот же плагин
работает и когда наказания лежат в собственной БД, и когда ими уже владеет сайт.

Интересное здесь не команды, а поведение при сбоях. Проверка бана стоит на критическом пути каждого
подключения, поэтому плагин отвечает из кеша, пока может; если сервис недоступен — использует
недавний протухший ответ; а когда пригодного нет вообще, применяет настраиваемую политику fail-open:
игрока пускают, инцидент пишется в лог. Заблокировать вход всей сети из-за перезапуска одного
внутреннего сервиса обычно хуже. `login.fail-open: false` переворачивает этот компромисс.

---

## Как устроено

```mermaid
flowchart LR
    P([Игрок]) -->|login| V[Velocity]
    V --> PL[ProxyPunishments — модуль Velocity]
    V -->|бэкенд-сервер| B[Сервер Paper]
    B --> PP[ProxyPunishments — модуль Paper]
    PL --> C{{Кеш Caffeine}}
    PP --> C2{{Кеш Caffeine}}
    PL -->|HTTP + X-Service-Token| S[proxypunishments-service]
    PP -->|HTTP + X-Service-Token| S
    S --> DB[(PostgreSQL)]
    S -.->|режим remote| W[API сайта]
    LP[LuckPerms] -.->|опционально| PL
```

Четыре Gradle-модуля, зависимости только в одну сторону:

| Модуль             | Содержимое                                                             |
|--------------------|------------------------------------------------------------------------|
| `modules/api`      | публичный SPI: `PunishmentService`, `ActivePunishmentView`, `Actor`    |
| `modules/core`     | HTTP-клиент, кеш, конфиг, пермишены, рендеринг MiniMessage              |
| `modules/velocity` | точка входа Velocity, команды, login-listener                           |
| `modules/paper`    | точка входа Paper, команды, pre-login-listener, listener мута в чате    |

В `api` и `core` нет ни одного типа платформы, поэтому вторая платформа — это только связующий код:
`modules/paper` добавляет деревья команд Brigadier, два листенера и `Actor`, а `PunishmentServiceImpl`,
`PunishmentCache`, `ConfigLoader` и `MessageBundle` использует как есть.

`core` общается с сервисом через интерфейс `PunishmentsClient`, поэтому всё, что выше сокета — кеш,
решение о входе, обработка команд — тестируется без сети.

## Какой модуль ставить

| | Модуль Velocity | Модуль Paper |
|---|---|---|
| Артефакт | `ProxyPunishments-1.0.0.jar` | `ProxyPunishments-Paper-1.0.0.jar` |
| Не пускает забаненных | да, на прокси | да, на бэкенд-сервере |
| Блокирует чат при муте | нет — подписанный чат не перехватить надёжно | **да** |
| Владеет командами | всегда | только на одиночном сервере |

Сеть за прокси: ставьте оба. Модуль Velocity выдаёт и снимает наказания и не пускает забаненных на
входе; модуль Paper существует ради того единственного, чего Velocity не умеет, — применения мутов,
потому что подписанный чат начиная с 1.19 делает перехват на прокси ненадёжным.

Одиночный сервер Paper без прокси: ставьте только модуль Paper. Он регистрирует полный набор команд
и применяет и баны, и муты.

**Кто владеет командами**: `commands.mode` решает, регистрирует ли модуль Paper команды вообще.
Значение по умолчанию `auto` не регистрирует ничего, если сервер сообщает о включённом форвардинге
BungeeCord или Velocity — там командами владеет прокси, — и регистрирует всё на одиночном сервере;
`always` и `never` перекрывают автоопределение. Выбранная ветка пишется в лог при старте, чтобы
администратору не пришлось гадать, куда делась команда.

Оба модуля читают один и тот же `config.yml` и `messages.yml` и ходят в один и тот же сервис,
поэтому мут, выданный на прокси, применяется модулем Paper без дополнительной настройки.

## Команды

Всё доступно как `/pp <подкоманда>` и, если не отключено, как команды верхнего уровня. Имена, уже
занятые другим плагином, пропускаются с предупреждением, а не перетираются. Оба модуля регистрируют
одно и то же дерево с одними и теми же пермишенами; на Paper то, регистрируются ли они вообще,
решает `commands.mode`.

| Команда       | Алиасы                                                | Пермишен                     |
|---------------|-------------------------------------------------------|------------------------------|
| `/ban`        | `permban`, `blacklist`, `banplayer`, `punish`         | `proxypunishments.command.ban`      |
| `/tempban`    | `tban`, `timedban`                                    | `proxypunishments.command.tempban`  |
| `/unban`      | `pardon`, `unblacklist`                               | `proxypunishments.command.unban`    |
| `/mute`       | `permmute`, `silence`, `muteplayer`                   | `proxypunishments.command.mute`     |
| `/tempmute`   | `tmute`, `timedmute`                                  | `proxypunishments.command.tempmute` |
| `/unmute`     | `unsilence`, `pardonmute`                             | `proxypunishments.command.unmute`   |
| `/warn`       | `caution`, `warnplayer`                               | `proxypunishments.command.warn`     |
| `/baninfo`    | `checkban`, `isbanned`, `banstatus`, `punishmentinfo` | `proxypunishments.command.info`     |
| `/banhistory` | `banlog`, `punishments`                               | `proxypunishments.command.history`  |
| `/banlist`    | `bans`, `activebans`, `punishmentlist`                | `proxypunishments.command.list`     |
| `/pp kick`    | —                                                     | `proxypunishments.command.kick`     |

`proxypunishments.admin` выдаёт все перечисленные.

**Длительности**: `30s`, `5m`, `2h30m`, `7d`, `1w`, `3M`, `1y`, либо `perm` / `forever` / `0`.
Регистр буквы значим: `m` — минуты, `M` — месяцы. Переполнение, нулевая и абсурдно большая
длительность отклоняются, а не превращаются в бан, истёкший в прошлом.

`/ban <ник> [длительность] <причина…>` берёт первое слово как длительность только если оно
действительно на неё похоже, поэтому `/ban Steve cheating with hacks` банит Стива за читы, а не
ругается на неверную длительность. `/mute` и `/tempmute` разбирают аргументы так же; `/warn <ник>
<причина…>` только пишется в журнал: ни длительности, ни снятия.

Муты выдаются на прокси, а применяет их Paper-модуль: подписанный чат начиная с 1.19 делает
перехват на прокси ненадёжным. `/baninfo <ник>` показывает и активный бан, и активный мут,
`/baninfo <ник> mute` и `/banlist [ban|mute] [страница]` сужают вывод до одного типа.

## Конфигурация

`plugins/proxypunishments/config.yml`:

```yaml
service:
  base-url: "http://proxypunishments:8080"
  token: ""              # должен совпадать с PROXYPUNISHMENTS_SERVICE_TOKEN на сервисе
  connect-timeout: PT2S
  request-timeout: PT3S

cache:
  active-ttl: PT30S      # свежий ответ отдаётся без обращения к сервису
  stale-ttl: PT30M       # сколько протухший ответ может прикрывать аварию
  max-size: 100000

login:
  fail-open: true
  remember-player: true

chat:                    # только модуль Paper
  enforce-mute: true
  fail-open: true        # сервис недоступен и в кеше пусто: пропустить сообщение
  blocked-commands: ["msg", "tell", "w", "whisper", "r", "reply", "me", "say", "pm"]

commands:
  register-top-level-aliases: true
  mode: auto             # только модуль Paper: auto | always | never
```

`chat.blocked-commands` перечисляет команды, которые по сути тот же чат: замученного игрока
останавливают и на `/msg`, и в чате. Префикс неймспейса вроде `essentials:msg` отбрасывается перед
сравнением, поэтому обойти мут через алиас не выйдет.

`plugins/proxypunishments/messages.yml` — все строки как шаблоны
[MiniMessage](https://docs.advntr.dev/minimessage/format.html). Значения плейсхолдеров
подставляются буквально и никогда не парсятся как разметка: игрок не сможет протащить форматирование,
hover или клик в сообщения для персонала через свой ник или причину бана. Ключи, добавленные в новых
версиях, берутся из встроенных дефолтов, а не рендерятся ошибкой.

## Пермишены

С установленным LuckPerms проверки идут через его player adapter. Без него работает нативная
проверка платформы: игрокам не даётся ничего, консоли — всё. Неверно настроенный сервер закроет
доступ персоналу, а не раздаст `/ban` всем подряд.

## Сборка

```bash
./gradlew build          # компиляция, тесты, shaded-jar
```

Артефакты: `modules/velocity/build/libs/ProxyPunishments-1.0.0.jar` и
`modules/paper/build/libs/ProxyPunishments-Paper-1.0.0.jar` → в папку `plugins/`.

Нужен JDK 25. Gradle wrapper закоммичен, ставить больше нечего.

Тулчейн и целевой байт-код намеренно разные: сборка идёт на тулчейне JDK 25, но компилируется с
`--release 21`, поэтому jar-ы загружаются на серверах с Java 21, которых у большинства и стоит.

## Тесты

```bash
./gradlew test
```

Покрыто намеренно там, где регрессия дороже всего:

- парсинг длительностей — месяцы против минут, переполнение, мусор на входе, разбор аргументов `/ban`
- поведение кеша — свежий, протухший, истёкший, кеширование отрицательного ответа (на управляемых часах)
- решение о логине — забанен, бан истёк, fail-open, fail-closed, применение протухшего кеша
- аргументы команд — разбор длительности мута, `/warn` без причины, тип и страница в `/banlist`
- HTTP-клиент — против настоящего сокета: экранирование запроса, не-JSON страницы ошибок, обрыв связи
- модуль Paper — автоопределение прокси для всех трёх значений `commands.mode`, решение о муте в чате
  (активен, истёк, сервис недоступен при обоих значениях fail-open), маппинг личности консоли
