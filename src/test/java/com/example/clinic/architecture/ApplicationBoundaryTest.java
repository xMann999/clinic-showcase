package com.example.clinic.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guards application entry-point signatures and controller injection boundaries using existing tooling.
 * Pure domain-rule components are not entry points. This checks declared types, not arbitrary method bodies.
 */
class ApplicationBoundaryTest {

    @Test
    void publicOperationsDoNotExposeDomainOrRepositoryTypes() {
        var entryPoints = Stream.concat(components(Service.class).stream(), components(RestController.class).stream());
        entryPoints.forEach(type -> {
            for (var method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                    assertBoundaryType(method.getGenericReturnType(), method.toString());
                    for (Type parameter : method.getGenericParameterTypes()) {
                        assertBoundaryType(parameter, method.toString());
                    }
                }
            }
        });
    }

    @Test
    void controllersDoNotInjectDomainOrRepositoryTypes() {
        for (Class<?> controller : components(RestController.class)) {
            for (var field : controller.getDeclaredFields()) {
                assertBoundaryType(field.getGenericType(), field.toString());
            }
            for (var constructor : controller.getDeclaredConstructors()) {
                for (Type parameter : constructor.getGenericParameterTypes()) {
                    assertBoundaryType(parameter, constructor.toString());
                }
            }
        }
    }

    private static List<Class<?>> components(Class<? extends Annotation> annotation) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        var definitions = scanner.findCandidateComponents("com.example.clinic");
        assertThat(definitions).as("Components annotated with %s", annotation.getSimpleName()).isNotEmpty();
        return definitions.stream()
            .<Class<?>>map(definition -> ClassUtils.resolveClassName(definition.getBeanClassName(), null))
            .toList();
    }

    private static void assertBoundaryType(Type type, String declaration) {
        assertThat(type.getTypeName())
            .as("Boundary type in %s", declaration)
            .doesNotContain("com.example.clinic.domain.");
        if (type instanceof Class<?> rawType) {
            assertThat(Repository.class.isAssignableFrom(rawType))
                .as("Repository dependency in %s", declaration)
                .isFalse();
        } else if (type instanceof ParameterizedType parameterized) {
            assertBoundaryType(parameterized.getRawType(), declaration);
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertBoundaryType(argument, declaration);
            }
        }
    }
}
