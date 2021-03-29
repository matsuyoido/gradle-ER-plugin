package com.matsuyoido.plugin.er;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class MainPluginTest {

    @TempDir
    Path projectDir;

    @Test
    public void taskCheck() throws Exception {
        String yamlFileName = classpathResourcePath("testcase/10_simple.yml");
        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    ddl {",
            "      yaml = file('" + yamlFileName + "')",
            "      outDir = file('./')",
            "      fileName = 'test'",
            "      schema = 'test1'",
            "      existCheck = false",
            "      truncate = false",
            "      lowerAll = false",
            "    }",
            "    ddl {",
            "      schema = 'test2'",
            "    }",
            "}"
        );


        String result = run("5.0", "tasks", "--all").getOutput();
        assertAll(
            () -> assertTrue(result.contains("Database tasks"), "contains database task"),
            () -> assertTrue(result.contains("ddl - YAML to DDL."), "check task contains.")
        );
    }

    @ParameterizedTest
    @CsvSource({
        ",testcase/00_all.yml,ddl-1.0.0.sql,testcase/01_result.sql",
        ",testcase/10_simple.yml,ddl.sql,testcase/11_result.sql",
        ",testcase/20_containsDomain.yml,ddl.sql,testcase/21_result.sql",
        ",testcase/30_containsKeys.yml,ddl.sql,testcase/31_result.sql"
    })
    public void ddlTaskExecute_noSchema(String testCase, String yamlFilePath, String expectFileName, String expectResultFilePath) throws Exception {
        String yamlFileName = classpathResourcePath(yamlFilePath);
        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    ddl {",
            "      yaml = file('" + yamlFileName + "')",
            "      outDir = file('./')",
            "    }",
            "}"
        );

        String result = run("5.0", "ddl").getOutput();
        assertTrue(result.contains("BUILD SUCCESSFUL"), "Task execute success.");

        File resultFile = projectDir.resolve(expectFileName).toFile();
        assertTrue(resultFile.exists(), testCase + " create ddl file exist?");
        Assertions.assertArrayEquals(
            Files.readAllLines(Path.of(classpathResourcePath(expectResultFilePath))).stream().filter(Predicate.not(String::isBlank)).toArray(),
            Files.readAllLines(resultFile.toPath()).stream().filter(Predicate.not(String::isBlank)).toArray(),
            testCase + " DDL content equals.");
    }

    @ParameterizedTest
    @CsvSource({
        ",testcase/00_all.yml,ddl-1.0.0.sql,testcase/02_result.sql",
        ",testcase/10_simple.yml,ddl.sql,testcase/12_result.sql",
        ",testcase/20_containsDomain.yml,ddl.sql,testcase/22_result.sql",
        ",testcase/30_containsKeys.yml,ddl.sql,testcase/32_result.sql"
    })
    public void ddlTaskExecute_existSchema(String testCase, String yamlFilePath, String expectFileName, String expectResultFilePath) throws Exception {
        String yamlFileName = classpathResourcePath(yamlFilePath);
        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    ddl {",
            "      yaml = file('" + yamlFileName + "')",
            "      outDir = file('./')",
            "      schema = 'test1'",
            "    }",
            "}"
        );

        run("5.0", "ddl").getOutput();

        File resultFile = projectDir.resolve(expectFileName).toFile();
        assertTrue(resultFile.exists(), testCase + " create ddl file exist?");
        Assertions.assertArrayEquals(
            Files.readAllLines(Path.of(classpathResourcePath(expectResultFilePath))).stream().filter(Predicate.not(String::isBlank)).toArray(),
            Files.readAllLines(resultFile.toPath()).stream().filter(Predicate.not(String::isBlank)).toArray(),
            testCase + " DDL content equals.");
    }

    @ParameterizedTest
    @CsvSource({
        ",testcase/00_all.yml,ddl-1.0.0.sql,testcase/03_result.sql",
        ",testcase/10_simple.yml,ddl.sql,testcase/13_result.sql",
        ",testcase/20_containsDomain.yml,ddl.sql,testcase/23_result.sql",
        ",testcase/30_containsKeys.yml,ddl.sql,testcase/33_result.sql"
    })
    public void ddlTaskExecute_existCheck(String testCase, String yamlFilePath, String expectFileName, String expectResultFilePath) throws Exception {
        String yamlFileName = classpathResourcePath(yamlFilePath);
        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    ddl {",
            "      yaml = file('" + yamlFileName + "')",
            "      outDir = file('./')",
            "      existCheck = true",
            "    }",
            "}"
        );

        run("5.0", "ddl").getOutput();

        File resultFile = projectDir.resolve(expectFileName).toFile();
        assertTrue(resultFile.exists(), testCase + " create ddl file exist?");
        Assertions.assertArrayEquals(
            Files.readAllLines(Path.of(classpathResourcePath(expectResultFilePath))).stream().filter(Predicate.not(String::isBlank)).toArray(),
            Files.readAllLines(resultFile.toPath()).stream().filter(Predicate.not(String::isBlank)).toArray(),
            testCase + " DDL content equals.");
    }

    @ParameterizedTest
    @CsvSource({
        ",testcase/00_all.yml,ddl-1.0.0.sql,testcase/04_result.sql",
        ",testcase/10_simple.yml,ddl.sql,testcase/14_result.sql",
        ",testcase/20_containsDomain.yml,ddl.sql,testcase/24_result.sql",
        ",testcase/30_containsKeys.yml,ddl.sql,testcase/34_result.sql"
    })
    public void ddlTaskExecute_lowerAll(String testCase, String yamlFilePath, String expectFileName, String expectResultFilePath) throws Exception {
        String yamlFileName = classpathResourcePath(yamlFilePath);
        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    ddl {",
            "      yaml = file('" + yamlFileName + "')",
            "      outDir = file('./')",
            "      lowerAll = true",
            "    }",
            "}"
        );

        run("5.0", "ddl").getOutput();

        File resultFile = projectDir.resolve(expectFileName).toFile();
        assertTrue(resultFile.exists(), testCase + " create ddl file exist?");
        Assertions.assertArrayEquals(
            Files.readAllLines(Path.of(classpathResourcePath(expectResultFilePath))).stream().filter(Predicate.not(String::isBlank)).toArray(),
            Files.readAllLines(resultFile.toPath()).stream().filter(Predicate.not(String::isBlank)).toArray(),
            testCase + " DDL content equals.");
    }

    @ParameterizedTest
    @CsvSource({
        ",testcase/45_result.sql,false,",
        ",testcase/44_result.sql,true,",
        ",testcase/42_result.sql,false,schema_test",
    })
    public void ddlTaskExecute_truncate(String testCase, String expectResultFilePath, boolean lowerAll, String schemaName) throws Exception {
        String yamlFileName = classpathResourcePath("testcase/40_truncateTest.yml");
        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    ddl {",
            "      yaml = file('" + yamlFileName + "')",
            "      outDir = file('./')",
            "      truncate = true",
            (lowerAll ? "      lowerAll = true" : ""),
            (schemaName != null ? "      schema = '" + schemaName + "'" : ""),
            "    }",
            "}"
        );

        run("5.0", "ddl").getOutput();

        File resultFile = projectDir.resolve("ddl.sql").toFile();
        assertTrue(resultFile.exists(), testCase + " create ddl file exist?");
        Assertions.assertArrayEquals(
            Files.readAllLines(Path.of(classpathResourcePath(expectResultFilePath))).stream().filter(Predicate.not(String::isBlank)).toArray(),
            Files.readAllLines(resultFile.toPath()).stream().filter(Predicate.not(String::isBlank)).toArray(),
            testCase + " DDL content equals.");
    }

    @Test
    public void erTaskExecute_minimum() throws Exception {
        String ddlFileName = classpathResourcePath("testcase/31_result.sql");

        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    er {",
            "      ddl = file('" + ddlFileName + "')",
            "      outDir = file('./er')",
            "    }",
            "}"
        );

        run("5.0", "er").getOutput();

        File resultFile = projectDir.resolve("er/index.html").toFile();
        assertTrue(resultFile.exists(), "ER html file exist?");
    }

    @Test
    public void erTaskExecute_schemaspyVersion() throws Exception {
        String ddlFileName = classpathResourcePath("testcase/31_result.sql");

        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    er {",
            "      version = '6.0.0'",
            "      ddl = file('" + ddlFileName + "')",
            "      outDir = file('./er')",
            "    }",
            "}"
        );

        run("5.0", "er").getOutput();

        File resultFile = projectDir.resolve("er/index.html").toFile();
        assertTrue(resultFile.exists(), "ER html file exist?");
    }

    @Test
    public void erTaskExecute_schemaspyVersion_notFound() throws Exception {
        String ddlFileName = classpathResourcePath("testcase/31_result.sql");

        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    er {",
            "      version = '5.0.0'",
            "      ddl = file('" + ddlFileName + "')",
            "      outDir = file('./er')",
            "    }",
            "}"
        );

        assertThrows(UnexpectedBuildFailure.class, () -> run("5.0", "er"));
    }

    private void setup(String... extensionText) throws IOException {
        projectDir.resolve("settings.gradle").toFile().createNewFile();
        File buildGradle = projectDir.resolve("build.gradle").toFile();
        buildGradle.createNewFile();
        createTestSettingFile();

        String classpath = Arrays.stream(System.getProperty("java.class.path").split(";"))
              .map(path -> String.format("'%s'", path))
              .collect(Collectors.joining(", "))
              .replace("\\", "/");
        StringBuilder buildScript = new StringBuilder();
        buildScript.append(String.join(System.lineSeparator(), 
            "buildscript {",
            "  dependencies {",
            "    classpath files(" + classpath + ")",
            "  }",
            "}",
            "import com.matsuyoido.plugin.er.MainPlugin",
            "apply plugin: MainPlugin")).append(System.lineSeparator());
        buildScript.append(Arrays.stream(extensionText).collect(Collectors.joining(System.lineSeparator())));
        String text = buildScript.toString();

        Files.writeString(buildGradle.toPath(), text);
    }

    private void createTestSettingFile() throws IOException {
        Path resourcePath = Path.of(classpathResourcePath("./"));

        Path gradleTestClasspathFile = resourcePath.resolve("plugin-under-test-metadata.properties");
        String classpathText = "implementation-classpath=" + System.getProperty("java.class.path");
        Files.writeString(gradleTestClasspathFile, classpathText);
    }
    private String classpathResourcePath(String path) {
        String uri = ClassLoader.getSystemResource(path).getPath();
        uri = uri.startsWith("/") ? uri.substring(1) : uri;
        return URLDecoder.decode(uri, StandardCharsets.UTF_8);
    }

    private BuildResult run(String gradleVersion, String... taskName) throws IOException {
        return GradleRunner.create()
                    .withGradleVersion(gradleVersion)
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withDebug(true)
                    .withArguments(taskName)
                    .forwardOutput()
                    .build();
    }

}
