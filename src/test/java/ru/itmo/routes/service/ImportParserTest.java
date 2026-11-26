package ru.itmo.routes.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportParserTest {
    private static final String ROW = """
            {"name":"Маршрут","coordinates":{"x":1.5,"y":2},
             "from":{"x":3,"y":4.5,"z":6},"distance":2,"rating":1}
            """.strip();

    @Test
    void readsInlineLocationsAndOptionalDestination() {
        var entry = parse("[" + ROW + "]").get(0);
        assertThat(entry.route().getName()).isEqualTo("Маршрут");
        assertThat(entry.route().getCoordinateX()).isEqualTo(1.5);
        assertThat(entry.route().getCoordinateY()).isEqualTo(2);
        assertThat(entry.from().getX()).isEqualTo(3);
        assertThat(entry.from().getY()).isEqualTo(4.5);
        assertThat(entry.from().getZ()).isEqualTo(6f);
        assertThat(entry.from().getName()).isNull();
        assertThat(entry.to()).isNull();
        var destination = parse("[" + ROW.replace("\"distance\":2", "\"to\":{\"x\":7,\"y\":8,\"z\":9,\"name\":\"Финиш\"},\"distance\":2") + "]")
                .get(0).to();
        assertThat(destination.getName()).isEqualTo("Финиш");
        assertThat(destination.getX()).isEqualTo(7);
    }

    @Test
    void allowsExplicitNullDestinationAndLocationName() {
        var entry = parse("[" + ROW.replace("\"distance\":2", "\"to\":null,\"distance\":2")
                .replace("\"z\":6", "\"z\":6,\"name\":null") + "]").get(0);
        assertThat(entry.to()).isNull();
        assertThat(entry.from().getName()).isNull();
    }

    @Test
    void rejectsMissingRequiredFieldsInsteadOfUsingFormDefaults() {
        for (String row : new String[]{ROW.replace("\"rating\":1", "\"to\":null"),
                ROW.replace("\"z\":6", "\"name\":null"),
                ROW.replace("\"x\":1.5,", ""), ROW.replace("\"from\":{\"x\":3,\"y\":4.5,\"z\":6},", "")}) {
            assertThatThrownBy(() -> parse("[" + row + "]"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Запись 1:");
        }
    }

    @Test
    void enforcesDomainRestrictionsWithRecordNumber() {
        for (String row : new String[]{ROW.replace("Маршрут", "  "), ROW.replace("\"distance\":2", "\"distance\":1"),
                ROW.replace("\"rating\":1", "\"rating\":0"), ROW.replace("\"z\":6", "\"z\":null"),
                ROW.replace("\"z\":6", "\"z\":6,\"name\":\"" + "x".repeat(756) + "\"")}) {
            assertThatThrownBy(() -> parse("[" + ROW + "," + row + "]"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Запись 2:");
        }
    }

    @Test
    void rejectsWrongTypesFractionsAndNumericOverflow() {
        for (String row : new String[]{ROW.replace("\"rating\":1", "\"rating\":\"1\""),
                ROW.replace("\"rating\":1", "\"rating\":2147483648"),
                ROW.replace("\"y\":2", "\"y\":9223372036854775808"),
                ROW.replace("\"y\":2", "\"y\":2.1"), ROW.replace("\"x\":1.5", "\"x\":1e400"),
                ROW.replace("\"z\":6", "\"z\":1e40"), ROW.replace("\"distance\":2", "\"distance\":1e40")}) {
            assertThatThrownBy(() -> parse("[" + row + "]")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsBoundaryIntegersWithoutRounding() {
        var entry = parse("[" + ROW.replace("\"y\":2", "\"y\":9223372036854775807")
                .replace("\"x\":3", "\"x\":-2147483648") + "]").get(0);
        assertThat(entry.route().getCoordinateY()).isEqualTo(Long.MAX_VALUE);
        assertThat(entry.from().getX()).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void rejectsUnknownFieldsAndServerGeneratedIdentifiers() {
        assertThatThrownBy(() -> parse("[" + ROW.replace("\"name\":", "\"id\":10,\"name\":") + "]"))
                .hasMessageContaining("Неизвестное поле: id");
    }

    @Test
    void rejectsMalformedEmptyAndOversizedInput() {
        for (String source : new String[]{"", "[]", "{}", "null", "[", "[null]", "[1]", "[" + ROW + "] {}"}) {
            assertThatThrownBy(() -> parse(source)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> ImportParser.parse(new byte[ImportParser.MAX_BYTES + 1]))
                .hasMessageContaining("1 МиБ");
        assertThatThrownBy(() -> parse("[" + (ROW + ",").repeat(ImportParser.MAX_RECORDS) + ROW + "]"))
                .hasMessageContaining("1000");
    }

    private static java.util.List<ImportParser.Entry> parse(String json) {
        return ImportParser.parse(json.getBytes(StandardCharsets.UTF_8));
    }
}
