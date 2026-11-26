# RouteHub — лабораторные работы 1 и 2

Jakarta EE 10 / JSF / EJB / Hibernate / PostgreSQL. Java 17+.

```sh
docker compose up --build -d
```

Приложение: http://localhost:8080/routes/ . Вход через BASIC-диалог браузера.

| Пользователь | Пароль | Роль |
|---|---|---|
| alice | Alice123! | user |
| bob | Bob12345! | user |
| admin | Admin123! | admin, user |
| admin2 | Admin234! | admin, user |

Это локальные учебные учётные записи. На своём WildFly их можно создать через `bin/add-user.sh -a`.
Миграции Flyway применяются при запуске; старые V1–V3 сохранены.

В меню «Импорт и история» загрузите [пример JSON](docs/examples/routes.json).
Имя маршрута (без учёта регистра и крайних пробелов) и пара координат должны быть уникальными.
Повторная загрузка примера завершится ошибкой и полным откатом.

```sh
mvn verify
jmeter -n -t docs/jmeter/lab2.jmx -l /tmp/lab2.jtl -j /tmp/lab2-jmeter.log
```

Команды запускаются из корня репозитория. JMeter 5.6.3 под Java 17; стенд должен работать.
Другой адрес: `-JbaseUrl=http://localhost:8080/routes`.
Сценарий добавляет тестовые данные с префиксом `JM-`; запускайте его на учебной БД.

- [Требования, устройство, атомарные задачи для двух участников и проверки](docs/LAB2.md).
- [Точные команды коммита и пуша двумя участниками](docs/COMMITS.md).

На macOS перед запуском JMeter выберите Java 17:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Groovy из JMeter 5.6.3 несовместим с Java 23, которая сейчас выбрана на этой машине по умолчанию.
