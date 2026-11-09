# Система бронирования отелей (Spring Boot, микросервисы)

Учебный проект: REST API для бронирования отелей на Spring Boot 3.5 и Spring Cloud.
Четыре отдельных приложения, у каждого сервиса своя база H2 в памяти.


| Модуль            | Порт | Что делает |
|-------------------|------|------------|
| `eureka-server`   | 8761 | реестр сервисов (Service Discovery) |
| `api-gateway`     | 8080 | Spring Cloud Gateway: маршрутизация, передаёт заголовок `Authorization` дальше |
| `booking-service` | 8081 | регистрация и вход (выдаёт JWT), пользователи, бронирования, согласование с hotel-service |
| `hotel-service`   | 8082 | отели, номера, временные блокировки номеров на даты, статистика загруженности |

Стек: Java 17, Spring Boot 3.5.16, Spring Cloud 2025.0.3 (Eureka, Gateway, LoadBalancer),
Spring Data JPA + H2, Spring Security (OAuth2 Resource Server, JWT), Lombok, MapStruct,
springdoc-openapi (Swagger UI), JUnit 5 + MockMvc + WireMock.

## Запуск

Нужны JDK 17+ и Maven 3.9+.

```bash
mvn clean package
```

Запускать по порядку, каждый в своём терминале (сначала Eureka):

```bash
java -jar eureka-server/target/eureka-server-1.0.0.jar
java -jar hotel-service/target/hotel-service-1.0.0.jar
java -jar booking-service/target/booking-service-1.0.0.jar
java -jar api-gateway/target/api-gateway-1.0.0.jar
```

Или из исходников: `mvn -pl eureka-server spring-boot:run` (и так же для остальных модулей).

Сервисы регистрируются в Eureka за 10–30 секунд, после этого шлюз начинает их видеть.
Список зарегистрированных сервисов: http://localhost:8761

При старте создаются тестовые данные:

- пользователи `admin` / `admin123` (ADMIN) и `user` / `user123` (USER);
- два отеля и пять номеров (`101`, `102`, `103` в «Гостинице Москва», `201`, `202` в «Невском отеле»).

Swagger UI (кнопка **Authorize** – вставить токен из `/user/auth`):

- booking-service: http://localhost:8081/swagger-ui.html
- hotel-service: http://localhost:8082/swagger-ui.html

## Как пользоваться

Все запросы идут через шлюз `http://localhost:8080`. Токен действует 1 час.

```bash
# регистрация (сразу возвращает токен) и вход
curl -X POST localhost:8080/api/bookings/user/register -H "Content-Type: application/json" \
     -d '{"username":"ivan","password":"ivan123"}'
curl -X POST localhost:8080/api/bookings/user/auth -H "Content-Type: application/json" \
     -d '{"username":"ivan","password":"ivan123"}'
# -> {"token":"eyJ...","tokenType":"Bearer","expiresInSeconds":3600,"username":"ivan","role":"USER"}

TOKEN=eyJ...

# отели и свободные номера на даты
curl localhost:8080/api/hotels -H "Authorization: Bearer $TOKEN"
curl "localhost:8080/api/rooms?startDate=2027-07-01&endDate=2027-07-05" -H "Authorization: Bearer $TOKEN"
# рекомендованные номера: свободные, по возрастанию times_booked
curl "localhost:8080/api/rooms/recommend?startDate=2027-07-01&endDate=2027-07-05" -H "Authorization: Bearer $TOKEN"

# бронирование конкретного номера
curl -X POST localhost:8080/api/bookings/booking -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"roomId":1,"startDate":"2027-07-01","endDate":"2027-07-05","autoSelect":false,"requestId":"my-req-1"}'

# бронирование с автоподбором номера (roomId игнорируется, hotelId - необязательный фильтр)
curl -X POST localhost:8080/api/bookings/booking -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"autoSelect":true,"hotelId":1,"startDate":"2027-07-01","endDate":"2027-07-05","requestId":"my-req-2"}'

# мои бронирования, одно бронирование, отмена
curl localhost:8080/api/bookings/bookings -H "Authorization: Bearer $TOKEN"
curl localhost:8080/api/bookings/booking/1 -H "Authorization: Bearer $TOKEN"
curl -X DELETE localhost:8080/api/bookings/booking/1 -H "Authorization: Bearer $TOKEN"
```

Администратор (токен от `admin` / `admin123`):

```bash
curl -X POST localhost:8080/api/hotels -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
     -d '{"name":"Отель Казань","address":"Казань, ул. Баумана, 1"}'
curl -X POST localhost:8080/api/rooms -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
     -d '{"hotelId":3,"number":"301"}'
curl localhost:8080/api/rooms/stats -H "Authorization: Bearer $ADMIN"
curl -X POST localhost:8080/api/bookings/user -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
     -d '{"username":"manager","password":"pass1234","role":"USER"}'
curl -X PATCH localhost:8080/api/bookings/user -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
     -d '{"id":3,"role":"ADMIN"}'
curl -X DELETE "localhost:8080/api/bookings/user?id=3" -H "Authorization: Bearer $ADMIN"
```

## Эндпойнты

### Gateway (порт 8080)

| Путь в шлюзе | Куда уходит |
|---|---|
| `/api/bookings/**` | booking-service, префикс `/api/bookings` отрезается (`/api/bookings/booking` → `/booking`) |
| `/api/hotels/**` | hotel-service |
| `/api/rooms`, `/api/rooms/recommend`, `/api/rooms/stats`, `/api/rooms/{id}` | hotel-service |
| `/api/rooms/{id}/confirm-availability`, `/api/rooms/{id}/release` | **не публикуются** (шлюз отвечает 404) |

### Booking Service (порт 8081)

| Метод и путь | Доступ | Описание |
|---|---|---|
| `POST /user/register` | все | регистрация, возвращает JWT |
| `POST /user/auth` | все | вход, возвращает JWT |
| `GET /user` | ADMIN | список пользователей |
| `POST /user` | ADMIN | создать пользователя (`username`, `password`, `role`) |
| `PATCH /user` | ADMIN | изменить пользователя (`id` в теле, остальные поля по желанию) |
| `DELETE /user?id=` | ADMIN | удалить пользователя |
| `POST /booking` | USER | создать бронирование (`roomId` или `autoSelect: true`), 201 – создано, 200 – повтор того же `requestId` |
| `GET /bookings` | USER | история своих бронирований |
| `GET /booking/{id}` | USER | своё бронирование по id (чужое – 404) |
| `DELETE /booking/{id}` | USER | отменить бронирование (снимает блокировку в hotel-service) |
| `GET /booking/recommend?startDate&endDate[&hotelId]` | USER | номера для автоподбора |

### Hotel Management Service (порт 8082)

| Метод и путь | Доступ | Описание |
|---|---|---|
| `GET /api/hotels`, `GET /api/hotels/{id}` | USER, ADMIN | отели |
| `POST /api/hotels`, `PUT /api/hotels/{id}`, `DELETE /api/hotels/{id}` | ADMIN | управление отелями |
| `GET /api/rooms[?startDate&endDate&hotelId]` | USER, ADMIN | свободные номера, без сортировки (по id) |
| `GET /api/rooms/recommend[?startDate&endDate&hotelId]` | USER, ADMIN | те же номера по возрастанию `times_booked`, при равенстве по id |
| `GET /api/rooms/{id}` | USER, ADMIN | номер |
| `POST /api/rooms`, `PUT /api/rooms/{id}`, `DELETE /api/rooms/{id}` | ADMIN | управление номерами |
| `GET /api/rooms/stats` | ADMIN | статистика загруженности: `timesBooked`, активные брони, занятые ночи |
| `POST /api/rooms/{id}/confirm-availability` | INTERNAL | временно заблокировать номер на даты |
| `POST /api/rooms/{id}/release` | INTERNAL | компенсация: снять блокировку |

Поле `available` у номера – это операционная доступность (ремонт и т.п.).
Занятость по датам считается по пересечению интервалов броней `[startDate, endDate)`: день выезда свободен для следующего гостя.

## Как устроено бронирование

```
Booking Service                                   Hotel Service
1. локальная транзакция: бронь PENDING
2. POST /api/rooms/{id}/confirm-availability ───▶ блокировка строки номера (SELECT ... FOR UPDATE),
   (тайм-аут, до 3 попыток, пауза 0.5 с, 1 с)     проверка requestId и пересечения дат,
                                                  запись блокировки, times_booked + 1
3a. 200 ─▶ локальная транзакция: CONFIRMED
3b. 409 (номер занят) ─▶ CANCELLED, ответ клиенту 409
3c. 5xx / тайм-аут после всех попыток ─▶ CANCELLED + POST /api/rooms/{id}/release (компенсация), ответ 503
```

- **Глобальных транзакций нет**: каждый шаг – своя короткая локальная транзакция (`@Transactional`),
  удалённый вызов делается вне транзакции.
- **Повторы и тайм-ауты**: тайм-аут соединения и чтения 3 с, до 3 попыток с паузой 500 мс → 1 с
  (настраивается в `booking-service/src/main/resources/application.yml`, блок `hotel`).
  Повторяются только 5xx, тайм-ауты и обрывы; ответы 4xx не повторяются.
- **Идемпотентность**: в запросе на бронирование передаётся `requestId`. Если бронь с таким `requestId`
  уже есть, возвращается она, новая не создаётся (уникальный индекс в БД защищает и от параллельной отправки).
  В hotel-service у блокировки тоже уникальный `requestId`: повтор `confirm-availability` после тайм-аута
  возвращает прежний результат и не увеличивает `times_booked` второй раз. `release` можно вызывать сколько угодно.
- **Компенсация**: при ошибке или тайм-ауте бронь переводится в CANCELLED, и вызывается `release`.
  Если hotel-service успел поставить блокировку (например, ответ не дошёл), она снимается; если нет – ничего не происходит.
- **Конкурентные брони**: hotel-service блокирует строку номера на время проверки, поэтому из нескольких
  одновременных запросов на пересекающиеся даты проходит ровно один, остальные получают 409.
- **Сквозная корреляция**: `bookingId` и `requestId` кладутся в MDC и передаются в hotel-service
  заголовками `X-Booking-Id`, `X-Request-Id`, поэтому они есть в каждой строке лога обоих сервисов:

```
INFO [bookingId=18 requestId=req-timeout] ... BookingStatusService : Бронирование 18 создано: PENDING, номер 7, 2027-06-01 - 2027-06-05
WARN [bookingId=18 requestId=req-timeout] ... HotelClient          : confirm-availability: попытка 1/3 не удалась: ... SocketTimeoutException: Read timed out
WARN [bookingId=18 requestId=req-timeout] ... HotelClient          : confirm-availability: попытка 3/3 не удалась: ... SocketTimeoutException: Read timed out
INFO [bookingId=18 requestId=req-timeout] ... BookingStatusService : Бронирование 18: PENDING -> CANCELLED (hotel-service не ответил)
INFO [bookingId=18 requestId=req-timeout] ... BookingService       : Компенсация для бронирования 18: блокировка номера 7 снята
```

### Автоподбор номера

hotel-service считает для каждого номера `times_booked` (успешные брони; при отмене счётчик уменьшается).
При `autoSelect: true` booking-service запрашивает `GET /api/rooms/recommend` – свободные на эти даты номера,
отсортированные по возрастанию `times_booked`, при равенстве по id – и бронирует первый.
Так новые брони уходят в номера, которые простаивали, и загрузка выравнивается.
Если первый номер успели занять, пробуются следующие (до трёх).

## Безопасность

- JWT выпускает booking-service при регистрации и входе, подпись HS256, срок жизни 1 час.
  В токене: `sub` – логин, `uid` – id пользователя, `role` – USER или ADMIN.
- Каждый сервис сам проверяет токен (Spring Security Resource Server), шлюз только маршрутизирует
  и передаёт заголовок `Authorization`.
- Внутренние вызовы `confirm-availability` и `release` booking-service делает с отдельным сервисным
  токеном (роль INTERNAL, живёт 5 минут), обычный пользователь и админ их вызвать не могут (403).
- Секрет подписи общий для сервисов, по умолчанию берётся из `application.yml`,
  в реальном запуске задаётся переменной окружения `JWT_SECRET` (не короче 32 символов).

## Тесты

```bash
mvn test
```

Тесты на JUnit 5 + MockMvc, hotel-service в тестах booking-service заменён заглушкой WireMock.

| Тест | Что проверяет |
|---|---|
| `booking-service` `BookingFlowTest` | успех (CONFIRMED); ошибка 500 → 3 попытки, CANCELLED, release; тайм-аут → CANCELLED, release; временная ошибка → повтор и CONFIRMED; 409 → без повторов и компенсации; повторная доставка с тем же `requestId` → без дубля; 5 параллельных одинаковых запросов → одна бронь; автоподбор и переход к следующему номеру; отмена пользователем; чужие брони не видны; в логах есть переходы состояний и результат компенсации |
| `booking-service` `AuthControllerTest` | регистрация и вход, токен на 1 час, 401/403, CRUD пользователей админом |
| `hotel-service` `AvailabilityTest` | блокировка номера, идемпотентность по `requestId`, 409 при пересечении дат, заезд в день выезда, release и его повтор, доступ к внутренним эндпойнтам |
| `hotel-service` `ConcurrentConfirmTest` | 10 потоков бронируют один номер на пересекающиеся даты – проходит ровно один |
| `hotel-service` `RoomControllerTest`, `HotelControllerTest` | сортировка рекомендаций, свободные номера по датам, статистика, права доступа, валидация |
| `api-gateway`, `eureka-server` | контекст поднимается, маршруты шлюза загружены |

## Что можно улучшить

- хранить секрет JWT в Vault или переменных окружения и перейти на асимметричные ключи (RS256 + JWKS);
- повторы и тайм-ауты сделать через Resilience4j (circuit breaker). Сейчас, если hotel-service
  лежит целиком, компенсация тоже не проходит: это пишется в лог с уровнем ERROR, а блокировка
  (если она успела встать) остаётся. Такие компенсации лучше складывать в очередь и повторять по расписанию;
- вместо H2 в памяти – PostgreSQL, тогда данные не пропадают при перезапуске;
- docker-compose для запуска всех сервисов одной командой.
