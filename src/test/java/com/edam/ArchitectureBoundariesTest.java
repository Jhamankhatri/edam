package com.edam;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces core modular monolith architecture rules:
 * constructor injection (no field @Autowired), no JPA entity leakage in web controllers,
 * and no cross-module access to another module's {@code internal} package.
 */
class ArchitectureBoundariesTest {

    private static final Pattern INTERNAL_IMPORT_PATTERN =
            Pattern.compile("^import\\s+com\\.edam\\.([a-z0-9_]+)\\.internal\\..*;$", Pattern.MULTILINE);

    @Test
    void controllersMustNotExposeJpaEntitiesInMethodSignatures() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> violations = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.edam")) {
            String className = bd.getBeanClassName();
            if (className == null || isTestClassName(className)) {
                continue;
            }
            Class<?> controllerClass = Class.forName(className);
            for (Method method : controllerClass.getDeclaredMethods()) {
                checkTypeNotEntity(controllerClass.getName() + "#" + method.getName() + " return",
                        method.getGenericReturnType(), violations);
                for (Type paramType : method.getGenericParameterTypes()) {
                    checkTypeNotEntity(controllerClass.getName() + "#" + method.getName() + " param",
                            paramType, violations);
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void productionClassesMustUseConstructorInjectionNotFieldAutowired() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);

        List<String> violations = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.edam")) {
            String className = bd.getBeanClassName();
            if (className == null || isTestClassName(className)) {
                continue;
            }
            Class<?> clazz = Class.forName(className);
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    violations.add(clazz.getName() + "." + field.getName());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void modulesMustNotImportAnotherModulesInternalPackage() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/edam");
        assertThat(Files.isDirectory(sourceRoot)).isTrue();

        List<String> violations = new ArrayList<>();
        try (var stream = Files.walk(sourceRoot)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = sourceRoot.relativize(file).toString();
                int slashIdx = relative.indexOf('/');
                String currentModule = slashIdx > 0 ? relative.substring(0, slashIdx) : "";
                String content = Files.readString(file);
                Matcher matcher = INTERNAL_IMPORT_PATTERN.matcher(content);
                while (matcher.find()) {
                    String importedModule = matcher.group(1);
                    if (!importedModule.equals(currentModule)) {
                        violations.add(relative + " -> " + matcher.group(0));
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    private boolean isTestClassName(String className) {
        return className.endsWith("Test")
                || className.endsWith("IT")
                || className.contains("Test$")
                || className.contains("IT$");
    }

    private void checkTypeNotEntity(String location, Type type, List<String> violations) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isAnnotationPresent(Entity.class)) {
                violations.add(location + " exposes @Entity " + clazz.getName());
            }
        } else if (type instanceof ParameterizedType pt) {
            for (Type arg : pt.getActualTypeArguments()) {
                checkTypeNotEntity(location, arg, violations);
            }
        }
    }
}
