package com.matsuyoido.plugin.er.task;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.matsuyoido.LineEnd;
import com.matsuyoido.plugin.er.DDLExtension;
import com.matsuyoido.plugin.er.RootExtension;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.yaml.snakeyaml.Yaml;

public class YamlDDLTask extends DefaultTask {

    private final String logFormat = "[YamlDDL] %s";
    private Logger log;
    private LineEnd lineEnd;

    @Override
    public String getDescription() {
        return "YAML to DDL.";
    }

    @TaskAction
    public void execute() {
        final Project project = getProject();
        final RootExtension extension = project.getExtensions().getByType(RootExtension.class);
        this.log = project.getLogger();
        this.lineEnd  = extension.getLineEnd();

        extension.getDDLConfig()
                 .forEach(this::action);
    }

    private void action(DDLExtension extension) {
        Yaml yaml = new Yaml();
        try {
            File yamlFile = extension.getYamlFile();
            File outputDir = extension.getOutputDir();
            if (yamlFile == null || outputDir == null) {
                log.warn(String.format(logFormat, "extension is invalid."));
                log.warn(getExtensionRequiredSetting());
                return;
            }
            if (!yamlFile.exists()) {
                log.warn(String.format(logFormat, "yaml file not found: {}"), yamlFile.getCanonicalPath());
                return;
            }
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            try (FileReader reader = new FileReader(yamlFile, StandardCharsets.UTF_8)) {
                Map<String, Object> yamlMap = yaml.loadAs(reader, MapWrapper.class);

                if (yamlMap == null || yamlMap.isEmpty()) {
                    log.warn(String.format(logFormat, "yaml file content is empty."));
                    return;
                }

                yamlMap.forEach((key, val) -> {
                    System.out.println(key);
                    System.out.println(val);
                });
            }
        } catch (IOException e) {
            log.error(String.format(logFormat, "yaml file load error."), e);
        }
    }

    class MapWrapper extends HashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    private String getExtensionRequiredSetting() {
        return String.join(System.lineSeparator(),
            "ddl task extension required minimum setting.",
            "******************************************************",
            "yamlER {",
            "    ddl {",
            "      yaml = file('database yaml file path')",
            "      outDir = file('ddl file output directory path')",
            "    }",
            "}",
            "******************************************************"
        );
    }

}
