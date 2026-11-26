package ru.itmo.routes.service;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import ru.itmo.routes.dto.LocationForm;
import ru.itmo.routes.dto.RouteForm;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ImportParser {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_RECORDS = 1000;

    public record Entry(RouteForm route, LocationForm from, LocationForm to) {}

    private ImportParser() {}

    public static List<Entry> parse(byte[] contents) {
        if (contents == null || contents.length == 0) throw invalid("Файл пуст");
        if (contents.length > MAX_BYTES) throw invalid("Файл должен быть не больше 1 МиБ");
        JsonArray array;
        try (var parser = Json.createParser(new ByteArrayInputStream(contents))) {
            if (!parser.hasNext() || parser.next() != jakarta.json.stream.JsonParser.Event.START_ARRAY) {
                throw invalid("Ожидается JSON-массив маршрутов");
            }
            array = parser.getArray();
            if (parser.hasNext()) throw invalid("После JSON-массива не должно быть других данных");
        } catch (JsonException exception) {
            throw invalid("Некорректный JSON");
        }
        if (array.isEmpty() || array.size() > MAX_RECORDS) {
            throw invalid("В файле должно быть от 1 до " + MAX_RECORDS + " записей");
        }
        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            try {
                JsonObject row = object(array.get(i), "маршрут");
                fields(row, Set.of("name", "coordinates", "from", "to", "distance", "rating"));
                RouteForm route = new RouteForm();
                String name = string(row.get("name"), "name");
                if (name.isBlank()) throw invalid("name: название обязательно");
                if (name.trim().length() > 255) throw invalid("name: не больше 255 символов");
                route.setName(name);
                JsonObject coordinates = object(row.get("coordinates"), "coordinates");
                fields(coordinates, Set.of("x", "y"));
                route.setCoordinateX(decimal(coordinates.get("x"), "coordinates.x"));
                route.setCoordinateY(integer(coordinates.get("y"), "coordinates.y"));
                route.setDistance(real(row.get("distance"), "distance"));
                if (route.getDistance() <= 1) throw invalid("distance: расстояние должно быть больше 1");
                route.setRating(smallInteger(row.get("rating"), "rating"));
                if (route.getRating() <= 0) throw invalid("rating: рейтинг должен быть больше 0");
                LocationForm from = location(object(row.get("from"), "from"), "from");
                JsonValue to = row.get("to");
                result.add(new Entry(route, from, to == null || to == JsonValue.NULL ? null
                        : location(object(to, "to"), "to")));
            } catch (IllegalArgumentException exception) {
                throw invalid("Запись " + (i + 1) + ": " + exception.getMessage());
            }
        }
        return result;
    }

    private static LocationForm location(JsonObject value, String path) {
        fields(value, Set.of("x", "y", "z", "name"));
        LocationForm form = new LocationForm();
        form.setX(smallInteger(value.get("x"), path + ".x"));
        form.setY(decimal(value.get("y"), path + ".y"));
        form.setZ(real(value.get("z"), path + ".z"));
        JsonValue name = value.get("name");
        if (name != null && name != JsonValue.NULL) {
            form.setName(string(name, path + ".name"));
            if (form.getName().length() > 755) throw invalid(path + ".name: не больше 755 символов");
        }
        return form;
    }

    private static JsonObject object(JsonValue value, String path) {
        if (value instanceof JsonObject object) return object;
        throw invalid(path + ": ожидается объект");
    }

    private static void fields(JsonObject value, Set<String> allowed) {
        for (String key : value.keySet()) {
            if (!allowed.contains(key)) throw invalid("Неизвестное поле: " + key);
        }
    }

    private static String string(JsonValue value, String path) {
        if (value instanceof JsonString string) return string.getString();
        throw invalid(path + ": ожидается строка");
    }

    private static JsonNumber number(JsonValue value, String path) {
        if (value instanceof JsonNumber number) return number;
        throw invalid(path + ": ожидается число");
    }

    private static long integer(JsonValue value, String path) {
        try {
            return number(value, path).longValueExact();
        } catch (ArithmeticException exception) {
            throw invalid(path + ": ожидается целое число в диапазоне long");
        }
    }

    private static int smallInteger(JsonValue value, String path) {
        long result = integer(value, path);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw invalid(path + ": число вне диапазона int");
        }
        return (int) result;
    }

    private static double decimal(JsonValue value, String path) {
        double result = number(value, path).doubleValue();
        if (!Double.isFinite(result)) throw invalid(path + ": число вне диапазона double");
        return result;
    }

    private static float real(JsonValue value, String path) {
        float result = (float) decimal(value, path);
        if (!Float.isFinite(result)) throw invalid(path + ": число вне диапазона float");
        return result;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
