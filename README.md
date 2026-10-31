# hhbot

Telegram-бот автооткликов на hh.ru.

- Java 21, Spring Boot 4
- PostgreSQL 17, Redis 8
- Telegram + Mini App
- публичный доступ по Telegram ID
- 100 откликов в сутки на пользователя, пауза 60–120 секунд
- круглосуточная очередь; вакансии закончились — повторный поиск через 15 минут
- автоматическая отправка или подтверждение кнопкой
- капча — стоп до ручной проверки

```sh
cp .env.example .env              # заполнить токен, пароль БД и ключ cookies
openssl rand -base64 32           # HH_COOKIE_KEY
docker compose up -d --build

./mvnw test
./mvnw package
```

`BOT_WEBAPP_URL` — HTTPS-адрес Mini App. `BOT_PUBLIC_ACCESS=false` — закрыть регистрацию.

Один экземпляр приложения. Очередь — в Redis, история откликов — в PostgreSQL, cookies зашифрованы.
