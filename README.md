# FoodMateBot

Личный Telegram-бот для двоих: выбор блюд на день, рецепты (с видео), история, избранное, список покупок, статистика и «блюдо дня» в группе.

## Стек

Java 17+, Spring Boot 3, Spring Data JPA, PostgreSQL, Liquibase, Telegram Long Polling, Docker Compose.

## Возможности

- Случайное блюдо (не предлагает недавно приготовленные)
- Добавление / редактирование / удаление рецептов (FSM)
- Видео к рецепту (можно переслать из другого чата)
- Избранное, история, поиск, фильтр по тегам
- Список покупок (добавление с рецепта + очистка)
- Оценка, комментарий после готовки
- Статистика и топ блюд
- «Блюдо дня» — сообщение в группу с закреплением
- Уведомления в семейную группу/тему (новый рецепт, оценка, покупки и т.д.)
- Whitelist по Telegram ID

## Быстрый старт (Docker)

1. Скопируй `.env.example` → `.env` и заполни значения.
2. **Останови локальный PostgreSQL**, если он занимает порт `5432`.
3. Запуск:

```bash
docker compose up -d --build
```

Остановка:

```bash
docker compose down
```

Пересоздать только приложение после правок `.env` (без пересборки образа):

```bash
docker compose up -d --force-recreate app
```

После изменений кода:

```bash
docker compose up -d --build app
```

### Без Docker

Нужен локальный Postgres с БД `menu_bot`, в `.env` URL на `localhost`, затем:

```bash
mvn spring-boot:run
```

## Переменные окружения

| Переменная | Описание |
|---|---|
| `BOT_TOKEN` | Токен от [@BotFather](https://t.me/BotFather) |
| `BOT_WHITELIST_IDS` | Telegram user ID через запятую. Пусто = пускает всех |
| `SPRING_DATASOURCE_URL` | JDBC URL (в Docker: `jdbc:postgresql://postgres:5432/menu_bot`) |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | Креды БД |
| `BOT_RECENT_DAYS` | Не рекомендовать блюда за N дней (по умолчанию 3) |
| `BOT_REMINDER_ENABLED` | Ежедневные напоминания в личку (`true`/`false`) |
| `BOT_REMINDER_CRON` / `BOT_REMINDER_TZ` | Расписание и часовой пояс напоминаний |
| `BOT_NOTIFY_CHAT_ID` | ID группы для уведомлений и «блюда дня» (например `-100…`) |
| `BOT_NOTIFY_THREAD_ID` | Опционально: ID темы форума. Если не задан — запоминается после первого сообщения боту в теме |

Узнать свой Telegram ID: [@userinfobot](https://t.me/userinfobot).

`.env` в git не коммитится. На другой машине: clone → скопировать `.env.example` → заполнить секреты.

## Команды в Telegram

- `/start`, `/menu` — главное меню
- `/cancel` — отмена ввода / FSM
- `/search …` или просто текст — поиск

В **группе с темами** пиши с username бота, например: `/start@YourBot` или `/menu@YourBot`, **внутри нужной темы**.

### Группа

1. Добавь бота в группу.
2. BotFather → бот → **Group Privacy → Turn off** (чтобы видел текст при добавлении рецептов).
3. Для «Блюдо дня»: сделай бота админом с правом **Закреплять сообщения**.
4. Один раз напиши боту в целевой теме — запомнится `thread_id` для уведомлений.

Ответы на кнопки идут в ту же тему, где было действие. Рассылки (оценка, покупки, новый рецепт) — в одну «домашнюю» тему (`BOT_NOTIFY_*`).

## База данных

При Docker Compose данные в контейнере `foodmate-postgres`, БД `menu_bot`, пользователь/пароль из compose (по умолчанию `postgres`/`postgres`).

Подключение из pgAdmin / DBeaver / IDEA:

- Host: `localhost`
- Port: `5432`
- Database: `menu_bot`
- User / password: как в `.env`

Или из терминала:

```bash
docker exec -it foodmate-postgres psql -U postgres -d menu_bot
```

Миграции — Liquibase (`src/main/resources/db/changelog/`).

## Круглосуточная работа

На домашнем ПК бот работает только пока включён компьютер. Для 24/7 — VPS с Docker (Timeweb, Selectel, Hetzner и т.п., ориентир 1–2 GB RAM): залить проект + `.env`, выполнить `docker compose up -d --build`. Long polling, отдельный домен не нужен.

## Полезные команды

```bash
docker compose logs app --tail 50    # логи бота
docker compose logs app -f           # логи в реальном времени
docker compose ps                    # статус контейнеров
```
