package com.matsuyoido.plugin.er;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MainPluginTest {

    @TempDir
    Path projectDir;

    @Test
    public void taskCheck() throws Exception {
        String yamlFileName = "test.yaml";
        setup(
            "yamlER {",
            "    lineEnding = 'linux'",
            "    ddl {",
            "      yaml = file('./" + yamlFileName + "')",
            "      outDir = file('./')",
            "      schema = 'test1'",
            "    }",
            "    ddl {",
            "      schema = 'test2'",
            "    }",
            "}"
        );

        String yamlText = String.join(System.lineSeparator(),
        "test: aaa"
        );
        Files.writeString(projectDir.resolve(yamlFileName), yamlText);

        // String result = run("5.0", "tasks", "--all").getOutput();
        String result = run("5.0", "ddl").getOutput();
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
