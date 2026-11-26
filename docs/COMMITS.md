# Коммиты и пуш двумя участниками

Участник 1 отвечает за импорт, историю и интерфейс, участник 2 — за конкуренцию и проверку.
Маленькие задачи A1–A16 / B1–B16 перечислены в [LAB2.md](LAB2.md).
Ниже они сгруппированы в семь собираемых коммитов. Первые четыре коммита опубликованы в `origin/lab2`. Ветка `lab2-concurrency` добавляет к ним три коммита второго участника.

Команды выполнять из `is-labs`. Перед началом проверьте `git status --short`.
Не добавляйте `target`, скачанный JMeter или его логи. Для JMeter 5.6.3 выберите Java 17 (на macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`). Пути в командах ниже покрывают подготовленные изменения.

## Участник 1 — четыре коммита

Настроить собственные реальные имя и почту локально в этом клоне:

```sh
git config --local user.name 'Имя первого участника'
git config --local user.email 'first@example.com'
git switch -c lab2
```

A1–A7: JSON-контракт, примеры, проверка ввода и тесты:

```sh
git add pom.xml \
  src/main/java/ru/itmo/routes/service/ImportParser.java \
  src/test/java/ru/itmo/routes/service/ImportParserTest.java \
  docs/examples/routes.json docs/examples/rollback.json
git diff --cached --stat
git commit -m "feat(import): parse and validate JSON route batches"
```

A8–A11: хранение истории и проверка владельца:

```sh
git add src/main/java/ru/itmo/routes/domain/ImportOperation.java \
  src/main/java/ru/itmo/routes/service/ImportHistory.java \
  src/test/java/ru/itmo/routes/service/ImportHistoryTest.java \
  src/main/resources/db/migration/V4__import_history.sql \
  src/main/resources/META-INF/persistence.xml
git commit -m "feat(import): persist import history with owner access"
```

A12–A14: транзакция пакета, статусы и проверка порядка завершения:

```sh
git add src/main/java/ru/itmo/routes/service/ImportTransaction.java \
  src/main/java/ru/itmo/routes/service/ImportService.java \
  src/test/java/ru/itmo/routes/service/ImportServiceTest.java
git commit -m "feat(import): commit batches atomically and retain failures"
```

A15–A16: загрузка файла, история в UI, авторизация и запуск:

```sh
git add src/main/java/ru/itmo/routes/web/ImportBean.java \
  src/main/webapp/import.xhtml \
  src/main/webapp/WEB-INF/templates/app.xhtml \
  src/main/webapp/WEB-INF/web.xml \
  src/main/webapp/WEB-INF/jboss-web.xml \
  Dockerfile compose.yaml .gitignore .dockerignore
git commit -m "feat(web): add import screen and container authentication"
git push -u origin lab2
```

В этой точке импорт компилируется, история и загрузка работают. Новые правила уникальности и защита
конкурентных изменений добавляются следующими коммитами участника 2. Проверку итоговой ЛР2 делайте после них.

## Основа второй ветки

Ветка `lab2-concurrency` создана поверх `origin/lab2`. Для проверки истории сравните эти две ветки.

## Участник 2 — три коммита

```sh
git config --local user.name 'Имя второго участника'
git config --local user.email 'second@example.com'
```

B1–B2, B7: версия маршрута, перенос через DTO/форму и учёт SQL-изменений:

```sh
git add src/main/java/ru/itmo/routes/domain/Route.java \
  src/main/java/ru/itmo/routes/dto/RouteDto.java \
  src/main/java/ru/itmo/routes/dto/RouteForm.java \
  src/main/java/ru/itmo/routes/service/RouteMapper.java \
  src/main/java/ru/itmo/routes/repository/RouteRepository.java \
  src/main/resources/db/migration/V5__route_version.sql
git commit -m "feat(routes): propagate route versions through edit forms"
```

B3–B6, B8: единые правила записи, проверка конфликтов и сообщения в UI:

```sh
git add src/main/java/ru/itmo/routes/service/BusinessException.java \
  src/main/java/ru/itmo/routes/service/RouteRules.java \
  src/main/java/ru/itmo/routes/service/RouteService.java \
  src/main/java/ru/itmo/routes/service/LocationService.java \
  src/main/java/ru/itmo/routes/web/RouteListBean.java \
  src/main/java/ru/itmo/routes/web/RouteDetailBean.java \
  src/main/java/ru/itmo/routes/web/SpecialOperationsBean.java \
  src/main/webapp/index.xhtml src/main/webapp/route-view.xhtml \
  docker/wildfly/configure.cli
git commit -m "fix(routes): enforce uniqueness and reject stale mutations"
```

B9–B16: JMeter, результаты проверки, обзор задач и инструкции:

```sh
git add docs/jmeter/lab2.jmx docs/jmeter/concurrency.groovy \
  docs/LAB2.md docs/COMMITS.md docs/VALIDATION.md README.md
git commit -m "test(lab2): verify concurrent CRUD and imports with JMeter"
mvn verify
docker compose up --build -d
jmeter -n -t docs/jmeter/lab2.jmx -l /tmp/lab2-final.jtl -j /tmp/lab2-final.log
git status --short
git -c remote.origin.pushurl=https://github.com/undndnwnkk/is-labs.git push -u origin lab2-concurrency
```

Открыть PR из `lab2-concurrency` в `main`: эта ветка содержит все семь коммитов обоих участников.
Отдельно сливать `lab2` не требуется. История авторства сохраняется без squash:

```sh
git log --format='%h %an <%ae> %s' main..lab2-concurrency
```
