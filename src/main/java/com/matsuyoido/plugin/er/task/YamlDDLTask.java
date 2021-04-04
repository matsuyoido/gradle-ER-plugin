package com.matsuyoido.plugin.er.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.matsuyoido.LineEnd;
import com.matsuyoido.ddl.DatabaseColumn;
import com.matsuyoido.ddl.DatabaseTable;
import com.matsuyoido.plugin.er.DDLExtension;
import com.matsuyoido.plugin.er.RootExtension;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;
import org.yaml.snakeyaml.Yaml;

public class YamlDDLTask extends DefaultTask {

    private final String logFormat = "[YamlDDL] %s";
    private final Logger log = getProject().getLogger();
    private final LineEnd lineEnd = getProject().getExtensions().getByType(RootExtension.class).getLineEnd();

    @Override
    public String getDescription() {
        return "YAML to DDL.";
    }

    @TaskAction
    public void execute() {
        final Project project = getProject();
        final RootExtension extension = project.getExtensions().getByType(RootExtension.class);

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

                DatabaseDefinition database = loadYamlFile(yamlMap);
                StringBuilder tableDDL = new StringBuilder();
                StringBuilder keyDDL = new StringBuilder();
                database.tables.forEach(table -> {
                    tableDDL.append(table.createSentence(extension.isIncludeExistCheck(), extension.isAllCharacterLowerCase(), extension.getSchema())
                                         .stream()
                                         .collect(Collectors.joining(this.lineEnd.get())))
                            .append(this.lineEnd.get());
                    keyDDL.append(this.lineEnd.get())
                          .append(table.foreignKeySentence(extension.isAllCharacterLowerCase(), extension.getSchema())
                                      .stream()
                                      .collect(Collectors.joining(this.lineEnd.get())))
                          .append(this.lineEnd.get());
                    keyDDL.append(this.lineEnd.get())
                          .append(table.uniqueKeySentence(extension.isAllCharacterLowerCase(), extension.getSchema())
                                       .stream()
                                       .collect(Collectors.joining(this.lineEnd.get())))
                          .append(this.lineEnd.get());
                    keyDDL.append(this.lineEnd.get())
                          .append(table.indexKeySentence(extension.isAllCharacterLowerCase(), extension.getSchema())
                                       .stream()
                                       .collect(Collectors.joining(this.lineEnd.get())))
                          .append(this.lineEnd.get());
                });

                StringBuilder truncateQuery = new StringBuilder();
                if (extension.isIncludeTruncateTable()) {
                    // テーブル一覧から、ForeignKey が付いているものを後回しにしていく
                    // 1から順に見ていく。ForeignKey があり、その関係テーブルよりも前にあれば、最後に移動させる。
                    // また、1から順に見ていく。同じことを繰り返し、問題ないようなら、文を作る。
                    Map<String, DatabaseTable> tableMap = database.tables.stream().collect(Collectors.toMap(DatabaseTable::getName, v -> v));
                    LinkedList<String> tables = new LinkedList<>(tableMap.keySet());

                    boolean changedOrder;
                    do {
                        changedOrder = false;
                        var iterator = tables.iterator();
                        while (iterator.hasNext()) {
                            DatabaseTable table = tableMap.get(iterator.next());
                            if (table.hasForeignKey()) {
                                int tableIndex = tables.indexOf(table.getName());
                                if (table.getRelationTables().stream().anyMatch(tableName -> tableIndex < tables.indexOf(tableName))) {
                                    tables.remove(table.getName());
                                    tables.addLast(table.getName());
                                    changedOrder = true;
                                    break;
                                }
                            }
                        }
                    } while(changedOrder);
                    tables.forEach(tableName -> 
                            truncateQuery.append(DatabaseTable.toSentence("TRUNCATE TABLE IF EXISTS ", extension.isAllCharacterLowerCase()))
                                         .append(extension.getSchema().map(v -> v + ".").orElse(""))
                                         .append(tableName)
                                         .append(";")
                                         .append(this.lineEnd.get())
                    );
                    truncateQuery.append(this.lineEnd.get());
                    tables.forEach(tableName ->
                            truncateQuery.append(DatabaseTable.toSentence("DROP TABLE IF EXISTS ", extension.isAllCharacterLowerCase()))
                                         .append(extension.getSchema().map(v -> v + ".").orElse(""))
                                         .append(tableName)
                                         .append(";")
                                         .append(this.lineEnd.get())
                    );
                    truncateQuery.append(this.lineEnd.get());
                }

                String outputFileName = extension.getFileName().orElse("ddl") + database.version.map(ver -> "-" + ver).orElse("") + ".sql";
                File outputFile = outputDir.toPath().resolve(outputFileName).toFile();
                try (FileOutputStream fos = new FileOutputStream(outputFile);
                        OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    writer.write(truncateQuery.toString() + tableDDL.toString() + keyDDL.toString());
                }
                log.lifecycle(String.format(logFormat, "ddl file created: " + outputFile.getCanonicalPath()));
            }
        } catch (IOException e) {
            log.error(String.format(logFormat, "yaml file load error: " + extension.getYamlFile().getName()), e);
            throw new GradleException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private DatabaseDefinition loadYamlFile(Map<String, Object> yamlMap) {
        Optional<String> version = Optional.ofNullable(yamlMap.get("version"))
                                           .map(String::valueOf);
        Map<String, String> domains = Optional.ofNullable(yamlMap.get("domains"))
                                              .filter(value -> value instanceof Map)
                                              .map(value -> (Map<String, String>) value)
                                              .orElseGet(Map::of);
        List<DatabaseColumn> commonColumns = yamlConvertColumns(Optional.ofNullable(yamlMap.get("commonColumns")), domains);
                                                     

        List<ForeignKeyInfo> foreignKeyInfos = new ArrayList<>();
        LinkedHashMap<String, DatabaseTable> tables = Optional.ofNullable(yamlMap.get("tables")).filter(value -> value instanceof Map)
                .map(value -> {
                    Map<String, Object> tableMap = (Map<String, Object>) value;
                    return tableMap.entrySet().stream().map(entry -> {
                        String tableName = entry.getKey();
                        Object entryValue = entry.getValue();
                        if (entryValue instanceof Map) {
                            Map<String, Object> tableDefinition = (Map<String, Object>) entryValue;
                            String tableComment = Optional.ofNullable(tableDefinition.get("info"))
                                                          .map(String::valueOf)
                                                          .orElse(null);
                            List<String> primaryKeys = Optional.ofNullable(tableDefinition.get("pk"))
                                                               .map(pkValue -> {
                                                                   if (pkValue instanceof List) {
                                                                    return (List<String>) pkValue;
                                                                   } else {
                                                                    return List.of(String.valueOf(pkValue));
                                                                   }
                                                               }).orElseGet(List::of);
                            Optional.ofNullable(tableDefinition.get("fk"))
                                    .filter(fkValue -> fkValue instanceof Map)
                                    .ifPresent(fkValue -> {
                                        Map<String, Object> foreignKeyMap = (Map<String, Object>) fkValue;
                                        foreignKeyMap.forEach((foreignKeyName, foreignKeyInfo) -> {
                                            if (foreignKeyInfo instanceof Map) {
                                                Map<String, Object> foreignKeyDefinition = (Map<String, Object>) foreignKeyInfo;
                                                Optional<List<String>> parentInfo = Optional.ofNullable(foreignKeyDefinition.get("relate"))
                                                        .map(parentColumnName -> {
                                                            if (parentColumnName instanceof List) {
                                                                return (List<String>) parentColumnName;
                                                            } else {
                                                                return List.of(String.valueOf(parentColumnName));
                                                            }
                                                        });
                                                Optional<AbstractMap.SimpleEntry<String, List<String>>> childInfo = Optional.ofNullable(foreignKeyDefinition.get("to"))
                                                        .flatMap(childRelation -> {
                                                            if (childRelation instanceof Map) {
                                                                Map<String, Object> childDefinition = (Map<String, Object>) childRelation;
                                                                return childDefinition.entrySet().stream()
                                                                               .map(childEntry -> {
                                                                                   String childTableName = childEntry.getKey();
                                                                                   if (childEntry.getValue() instanceof List) {
                                                                                       return new AbstractMap.SimpleEntry<>(childTableName, ((List<String>) childEntry.getValue()));
                                                                                   } else {
                                                                                       return new AbstractMap.SimpleEntry<>(childTableName, List.of(String.valueOf(childEntry.getValue())));
                                                                                   }
                                                                               }).findFirst();
                                                            } else {
                                                                return null;
                                                            }
                                                        }).filter(Objects::nonNull);
                                                if (parentInfo.isPresent() && childInfo.isPresent()) {
                                                    String childTableName = childInfo.get().getKey();
                                                    List<String> childRelationColumns = childInfo.get().getValue();
                                                    foreignKeyInfos.add(new ForeignKeyInfo(foreignKeyName, tableName, parentInfo.get(), childTableName, childRelationColumns));
                                                }
                                            }
                                        });
                                    });
                            DatabaseTable table = new DatabaseTable(tableName, tableComment);
                            Optional.ofNullable(tableDefinition.get("logicalName"))
                                    .map(String::valueOf)
                                    .ifPresent(logicalName -> table.logicalName(logicalName));
                            table.addColumns(yamlConvertColumns(Optional.ofNullable(tableDefinition.get("columns")), domains));
                            if (!table.addPrimaryKey(primaryKeys.toArray(String[]::new))) {
                                log.warn(String.format(logFormat, "PK: columns not found. [" + primaryKeys.stream().collect(Collectors.joining(" | ")) + "]"));
                            }
                            Optional.ofNullable(tableDefinition.get("uq"))
                                    .filter(uqValue -> uqValue instanceof Map)
                                    .ifPresent(uqValue -> {
                                        Map<String, Object> uniqueKeyDefinition = (Map<String, Object>) uqValue;
                                        uniqueKeyDefinition.forEach((uniqueKeyName, uniqueColumnValue) -> {
                                            if (uniqueColumnValue instanceof List) {
                                                List<String> uniqueColumns = (List<String>) uniqueColumnValue;
                                                if (!table.addUniqueKey(uniqueKeyName, uniqueColumns.toArray(String[]::new))) {
                                                    log.warn(String.format(logFormat, "UK: columns not found. [" + uniqueColumns.stream().collect(Collectors.joining(" | ")) + "]"));
                                                }
                                            } else {
                                                if (!table.addUniqueKey(uniqueKeyName, String.valueOf(uniqueColumnValue))) {
                                                    log.warn(String.format(logFormat, "UK: columns not found. [" + uniqueColumnValue + "]"));
                                                }
                                            }
                                        });
                                    });
                            Optional.ofNullable(tableDefinition.get("idx"))
                                    .filter(idxValue -> idxValue instanceof Map)
                                    .ifPresent(idxValue -> {
                                        Map<String, Object> indexDefinition = (Map<String, Object>) idxValue;
                                        indexDefinition.forEach((indexName, indexColumnValue) -> {
                                            if (indexColumnValue instanceof List) {
                                                List<String> indexColumns = (List<String>) indexColumnValue;
                                                if (!table.addIndexKey(indexName, indexColumns.toArray(String[]::new))) {
                                                    log.warn(String.format(logFormat, "IDX: columns not found. [" + indexColumns.stream().collect(Collectors.joining(" | ")) + "]"));
                                                }
                                            } else {
                                                if (!table.addIndexKey(indexName, String.valueOf(indexColumnValue))) {
                                                    log.warn(String.format(logFormat, "IDX: columns not found. [" + indexColumnValue + "]"));
                                                }
                                            }
                                        });
                                    });
                            return table;
                        } else {
                            return new DatabaseTable(tableName);
                        }
                    }).collect(Collectors.toMap(
                        DatabaseTable::getName,
                        v -> v,
                        (a, b) -> b,
                        LinkedHashMap::new
                    ));
                }).orElseGet(LinkedHashMap::new);
        tables.values().forEach(table -> table.addColumns(commonColumns));
        foreignKeyInfos.forEach(info -> {
            DatabaseTable foreignKeyAddTable = tables.get(info.parentTableName);
            if (tables.containsKey(info.childTableName)) {
                boolean addSuccessed = foreignKeyAddTable.addForeignKey(info.keyName, info.parentRelationColumnNames, tables.get(info.childTableName), info.childRelationColumnNames);
                if (!addSuccessed) {
                    log.warn(String.format(logFormat, "FK: [" + info.keyName + "] relation column not found, reconfirm. Parent: [" + info.parentRelationColumnNames.stream().collect(Collectors.joining(" | ")), "] Child: [" + info.childRelationColumnNames.stream().collect(Collectors.joining(" | ")) + "]"));    
                }
            } else {
                log.warn(String.format(logFormat, "FK: [" + info.keyName + "] child table not found. " + info.childTableName));
            }
        });
        return new DatabaseDefinition(version, new ArrayList<>(tables.values()));
    }


    @SuppressWarnings("unchecked")
    private List<DatabaseColumn> yamlConvertColumns(Optional<Object> columnValue, Map<String, String> domains) {
        return columnValue.filter(value -> value instanceof Map)
        .map(value -> {
            Map<String, Object> columnMap = (Map<String, Object>) value;
            return columnMap.entrySet()
                            .stream()
                            .map(entry -> {
                                String columnName = entry.getKey();
                                Object entryValue = entry.getValue();
                                if (entryValue instanceof Map) {
                                    Map<String, Object> columnDefinition = (Map<String, Object>) entryValue;

                                    String type = Optional.ofNullable(columnDefinition.get("type"))
                                                          .map(columnType -> {
                                                              String definition = String.valueOf(columnType);
                                                              return domains.getOrDefault(definition, definition);
                                                          }).orElse(null);
                                    String logicalName = Optional.ofNullable(columnDefinition.get("logicalName"))
                                                                 .map(String::valueOf)
                                                                 .orElse(null);
                                    String comment = Optional.ofNullable(columnDefinition.get("info"))
                                                             .map(String::valueOf)
                                                             .orElse(null);
                                    String constraints = Optional.ofNullable(columnDefinition.get("options"))
                                                                 .filter(optionValue -> optionValue instanceof String)
                                                                 .map(String::valueOf)
                                                                 .orElse(null);
                                    String defaultValue = Optional.ofNullable(columnDefinition.get("defaultValue"))
                                                                  .map(String::valueOf)
                                                                  .orElse(null);
                                    return new DatabaseColumn(columnName, type)
                                       .logicalName(logicalName)
                                       .comment(comment)
                                       .constraints(constraints)
                                       .defaultValue(defaultValue);
                                } else {
                                    return null;
                                }
                            }).filter(Objects::nonNull)
                            .collect(Collectors.toList());
        }).orElseGet(List::of);
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
