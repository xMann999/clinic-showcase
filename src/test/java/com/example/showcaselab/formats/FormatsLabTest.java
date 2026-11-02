package com.example.showcaselab.formats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ta sama mała struktura pacjenta w czterech formatach tekstowych.
 * <h2>Cel</h2>
 * Porównać reprezentację zagnieżdżeń, kolekcji i typów w czterech formatach tekstowych.
 * <h2>Jak działa przykład</h2>
 * Pliki: src/test/resources/labs/formats/patient.{json,yaml,properties,xml}
 * Gdzie te formaty żyją w projekcie: JSON - API; XML - pom.xml; YAML - migracje Liquibase;
 * properties - application.properties. Zagnieżdżony adres i dwa telefony istnieją wyłącznie
 * w tym fixture - nie rozszerzają encji Patient.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>JSON zachowuje typy i znaki specjalne.</li>
 * <li>Błędna składnia JSON jest odrzucana.</li>
 * <li>YAML odtwarza zagnieżdżoną strukturę i typy.</li>
 * <li>Properties przechowuje płaskie klucze i tekst.</li>
 * <li>XML rozróżnia elementy i atrybuty.</li>
 * <li>Drzewo JSON pozwala czytać bez klasy docelowej.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Format tekstowy i biblioteka odczytująca wspólnie określają reprezentację typów.
 */
@DisplayName("Ta sama mała struktura pacjenta w czterech formatach tekstowych.")
class FormatsLabTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    record Address(String street, String city) {}

    record PatientDocument(
        String firstName,
        String lastName,
        Address address,
        List<String> phones,
        boolean active,
        int visits
    ) {}

    /**
     * JSON zachowuje typy i znaki specjalne.
     */
    @Test
    @DisplayName("JSON zachowuje typy i znaki specjalne")
    void shouldPreserveTypesWhenReadingJson() throws Exception {
        try (InputStream in = resource("patient.json")) {
            PatientDocument patient = mapper.readValue(in, PatientDocument.class);

            assertThat(patient.address().city()).isEqualTo("Warszawa");
            assertThat(patient.phones()).containsExactly("+48600700800", "+48221234567");
            assertThat(patient.lastName()).isEqualTo("Nowak \"Ania\"");
            assertThat(patient.active()).isTrue();
            assertThat(patient.visits()).isEqualTo(3);
        }
    }

    /**
     * Błędna składnia JSON jest odrzucana.
     * Niezaescapowany cudzysłów wewnątrz tekstu i przecinek po ostatnim elemencie.
     */
    @Test
    @DisplayName("Błędna składnia JSON jest odrzucana")
    void shouldRejectMalformedInputWhenParsingJson() {
        assertThatThrownBy(() -> mapper.readTree("{\"lastName\":\"Nowak \"Ania\"\"}")).isInstanceOf(
            StreamReadException.class
        );
        assertThatThrownBy(() -> mapper.readTree("{\"phones\":[\"1\",\"2\",]}")).isInstanceOf(
            StreamReadException.class
        );
    }

    /**
     * YAML odtwarza zagnieżdżoną strukturę i typy.
     * YAML rozpoznaje typy: true to boolean, 3 to liczba.
     */
    @Test
    @DisplayName("YAML odtwarza zagnieżdżoną strukturę i typy")
    @SuppressWarnings("unchecked")
    void shouldPreserveStructureWhenReadingYaml() throws Exception {
        try (InputStream in = resource("patient.yaml")) {
            Map<String, Object> yaml = new Yaml().load(in);

            assertThat(((Map<String, Object>) yaml.get("address")).get("city")).isEqualTo("Warszawa");
            assertThat((List<String>) yaml.get("phones")).hasSize(2);

            assertThat(yaml.get("active")).isEqualTo(true);
            assertThat(yaml.get("visits")).isEqualTo(3);
        }
    }

    /**
     * Properties przechowuje płaskie klucze i tekst.
     * Zagnieżdżenie tylko w nazwie klucza, tablica przez indeksy; każda wartość to String.
     */
    @Test
    @DisplayName("Properties przechowuje płaskie klucze i tekst")
    void shouldReadTextWhenLoadingProperties() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = resource("patient.properties")) {
            properties.load(in);
        }

        assertThat(properties.getProperty("patient.address.city")).isEqualTo("Warszawa");
        assertThat(properties.getProperty("patient.phones[1]")).isEqualTo("+48221234567");
        assertThat(properties.getProperty("patient.visits")).isEqualTo("3");
    }

    /**
     * XML rozróżnia elementy i atrybuty.
     */
    @Test
    @DisplayName("XML rozróżnia elementy i atrybuty")
    void shouldReadElementsAndAttributesWhenParsingXml() throws Exception {
        try (InputStream in = resource("patient.xml")) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            Element root = document.getDocumentElement();

            assertThat(root.getAttribute("active")).isEqualTo("true");
            assertThat(root.getElementsByTagName("city").item(0).getTextContent()).isEqualTo("Warszawa");
            assertThat(root.getElementsByTagName("phone").getLength()).isEqualTo(2);
        }
    }

    /**
     * Drzewo JSON pozwala czytać bez klasy docelowej.
     */
    @Test
    @DisplayName("Drzewo JSON pozwala czytać bez klasy docelowej")
    void shouldNavigateTreeWhenNoTargetTypeIsProvided() throws Exception {
        try (InputStream in = resource("patient.json")) {
            JsonNode tree = mapper.readTree(in);
            assertThat(tree.get("phones").isArray()).isTrue();
            assertThat(tree.path("address").path("street").asString()).isEqualTo("ul. Prosta 1");
            assertThat(tree.path("missing").isMissingNode()).isTrue();
        }
    }

    private static InputStream resource(String name) {
        return FormatsLabTest.class.getResourceAsStream("/labs/formats/" + name);
    }
}
