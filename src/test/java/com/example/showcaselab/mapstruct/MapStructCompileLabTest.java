package com.example.showcaselab.mapstruct;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.Mapper;

/**
 * Kontrolowana próba dodania pola do DTO bez aktualizacji mappera.
 * <h2>Cel</h2>
 * Sprawdzić na rzeczywistym kompilatorze, że brak mapowania pola docelowego jest błędem kompilacji.
 * <h2>Jak działa przykład</h2>
 * Kompilujemy w locie dwie wersje tego samego mappera z procesorem MapStruct i polityką
 * unmappedTargetPolicy=ERROR (tak jak w pom.xml aplikacji). Druga wersja DTO ma nowe pole
 * "nickname", którego źródło nie ma - kompilacja kończy się błędem, zamiast wypuścić
 * odpowiedź z cichym null. Główny build nie zawiera celowo zepsutego kodu.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Kompletny mapper przechodzi kompilację.</li>
 * <li>Nowe pole docelowe bez źródła zatrzymuje kompilację.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * MapStruct jest uruchamiany rzeczywiście. Refleksyjny wariant jest uproszczonym, celowo niepełnym algorytmem.
 */
@DisplayName("Kontrolowana próba dodania pola do DTO bez aktualizacji mappera.")
class MapStructCompileLabTest {

    private static final String SOURCE =
        """
        package lab;
        import org.mapstruct.Mapper;
        import org.mapstruct.ReportingPolicy;
        @Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
        public interface LabMapper {
            record Source(String name) {}
            record Target(String name%s) {}
            Target map(Source source);
        }
        """;

    @TempDir
    Path output;

    /**
     * Kompletny mapper przechodzi kompilację.
     */
    @Test
    @DisplayName("Kompletny mapper przechodzi kompilację")
    void shouldCompileWhenAllTargetsAreMapped() {
        assertThat(compile(SOURCE.formatted(""))).isEmpty();
    }

    /**
     * Nowe pole docelowe bez źródła zatrzymuje kompilację.
     */
    @Test
    @DisplayName("Nowe pole docelowe bez źródła zatrzymuje kompilację")
    void shouldRejectCompilationWhenTargetFieldIsUnmapped() {
        List<String> errors = compile(SOURCE.formatted(", String nickname"));

        assertThat(errors).anySatisfy(message -> assertThat(message).contains("Unmapped target property: \"nickname\"")
        );
    }

    private List<String> compile(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        String classpath = jarOf(Mapper.class);
        String processorPath = processorJar(classpath) + File.pathSeparator + classpath;
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavaFileObject unit = new SimpleJavaFileObject(
                URI.create("string:///lab/LabMapper.java"),
                JavaFileObject.Kind.SOURCE
            ) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            };
            List<String> options = List.of(
                "-classpath",
                classpath,
                "-processorpath",
                processorPath,
                "-d",
                output.toString(),
                "-proc:full"
            );
            compiler.getTask(null, files, diagnostics, options, null, List.of(unit)).call();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        return diagnostics
            .getDiagnostics()
            .stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(diagnostic -> diagnostic.getMessage(null))
            .toList();
    }

    /**
     * Procesor leży w lokalnym repozytorium Maven obok biblioteki z adnotacjami:
     * .../org/mapstruct/mapstruct/1.6.3/mapstruct-1.6.3.jar
     * .../org/mapstruct/mapstruct-processor/1.6.3/mapstruct-processor-1.6.3.jar
     * (pobiera go zależność testowa mapstruct-processor w pom.xml).
     */
    private static String processorJar(String mapstructJar) {
        Path annotationsJar = Path.of(mapstructJar);
        String version = annotationsJar.getParent().getFileName().toString();
        Path processor = annotationsJar
            .getParent()
            .getParent()
            .getParent()
            .resolve("mapstruct-processor")
            .resolve(version)
            .resolve("mapstruct-processor-" + version + ".jar");
        assertThat(processor).exists();
        return processor.toString();
    }

    private static String jarOf(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
