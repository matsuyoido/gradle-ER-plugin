package com.matsuyoido.ddl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DatabaseTable {

    /** テーブル名 */
    private final String name;
    /** テーブルコメント */
    private final String comment;
    /** 論理名 */
    private String logicalName;

    /** テーブルカラム */
    private LinkedList<DatabaseColumn> columns;

    /** PrimaryKeyカラム */
    private LinkedList<DatabaseColumn> primaryKeyCloumns;

    /** ForeignKeyカラム */
    private List<DatabaseRelationKey> foreignKeyColumns;

    /** UniqueKeyカラム */
    private List<DatabaseKey> uniqueKeyColumns;

    /** Indexカラム */
    private List<DatabaseKey> indexColumns;

    public DatabaseTable(String tableName) {
        this(tableName, null);
    }

    public DatabaseTable(String tableName, String tableComment) {
        this.name = tableName;
        this.comment = tableComment;
        
        this.columns = new LinkedList<>();
        this.primaryKeyCloumns = new LinkedList<>();
        this.foreignKeyColumns = new ArrayList<>();
        this.uniqueKeyColumns = new ArrayList<>();
        this.indexColumns = new ArrayList<>();
    }

    public DatabaseTable logicalName(String name) {
        this.logicalName = name;
        return this;
    }

    public void addColumns(List<DatabaseColumn> columns) {
        this.columns.addAll(columns);
    }

    // public DatabaseColumn addColumn(String columnName, String columnType) {
    //     DatabaseColumn column = new DatabaseColumn(columnName, columnType);
    //     this.columns.add(column);
    //     return column;
    // }

    /** @return PK追加成功 */
    public boolean addPrimaryKey(String... columns) {
        Arrays.stream(columns)
              .map(name -> this.columns.stream().filter(column -> column.name.equals(name)).findFirst().orElse(null))
              .filter(Objects::nonNull)
              .forEach(column -> {
                  this.primaryKeyCloumns.add(column);
              });
        if (this.primaryKeyCloumns.size() != columns.length) {
            this.primaryKeyCloumns.clear();
            return false;
        } else {
            return true;
        }
    }

    /** @return FK追加成功 */
    public boolean addForeignKey(String keyName, List<String> columnNames, DatabaseTable table, List<String> childColumnNames) {
        DatabaseRelationKey key = new DatabaseRelationKey(keyName, this.name, table.name);
        List<DatabaseColumn> foreignColumns = this.columns.stream()
                                                          .filter(column -> columnNames.contains(column.name))
                                                          .collect(Collectors.toList());
        List<DatabaseColumn> relationColumns = table.columns.stream()
                                                            .filter(column -> childColumnNames.contains(column.name))
                                                            .collect(Collectors.toList());
        if (foreignColumns.size() != columnNames.size()) {
            return false;
        }
        if (relationColumns.size() != childColumnNames.size()) {
            return false;
        }
        key.setColumns(foreignColumns);
        key.setChildColumns(relationColumns);
        this.foreignKeyColumns.add(key);
        return true;
    }

    /** @return UK追加成功 */
    public boolean addUniqueKey(String keyName, String... uniqueColumnNames) {
        List<DatabaseColumn> columns = getColumns(uniqueColumnNames);
        if (columns.size() != uniqueColumnNames.length) {
            return false;
        }
        DatabaseKey uniqueKey = new DatabaseKey(keyName, columns);
        this.uniqueKeyColumns.add(uniqueKey);
        return true;
    }

    /** @return Idx追加成功 */
    public boolean addIndexKey(String keyName, String... indexColumnNames) {
        List<DatabaseColumn> columns = getColumns(indexColumnNames);
        if (columns.size() != indexColumnNames.length) {
            return false;
        }
        DatabaseKey indexKey = new DatabaseKey(keyName, columns);
        this.indexColumns.add(indexKey);
        return true;
    }

    private List<DatabaseColumn> getColumns(String[] columnArray) {
        List<String> names = Arrays.asList(columnArray);
        return this.columns.stream()
                   .filter(column -> names.contains(column.name))
                   .collect(Collectors.toList());
    }


    public String getName() {
        return this.name;
    }
    public boolean hasForeignKey() {
        return !this.foreignKeyColumns.isEmpty();
    }
    public Set<String> getRelationTables() {
        return this.foreignKeyColumns.stream().map(relation -> relation.relationTableName).collect(Collectors.toSet());
    }

    public List<String> createSentence(boolean existCheck, boolean lowerAll, Optional<String> schema) {
        LinkedList<String> sentence = new LinkedList<>();

        this.columns.forEach(column -> sentence.add("  " + column.createSentence(lowerAll)));
        if (!primaryKeyCloumns.isEmpty()) {
            sentence.add(toSentence("  PRIMARY KEY (", lowerAll) + this.primaryKeyCloumns.stream().map(column -> column.name).collect(Collectors.joining(", ")) + ")" );   
        }
        if (this.comment != null && !this.comment.isBlank()) {
            sentence.add(toSentence("  COMMENT ", lowerAll) + '"' + this.comment + '"');
        }

        sentence.replaceAll(text -> 
            text + (sentence.getLast().equals(text) ? "" : ",")
        );

        if (existCheck) {
            sentence.addFirst(toSentence("CREATE TABLE IF NOT EXISTS ", lowerAll) + schema.map(v -> v + ".").orElse("") + this.name + "(");
        } else {
            sentence.addFirst(toSentence("CREATE TABLE ", lowerAll) + schema.map(v -> v + ".").orElse("") + this.name + "(");
        }
        sentence.addLast(");");

        sentence.addFirst("-- " + this.name + Optional.ofNullable(this.logicalName).map(v -> " : " + v).orElse(""));
        return sentence;
    }


    public List<String> foreignKeySentence(boolean lowerAll, Optional<String> schema) {
        ArrayList<String> sentence = new ArrayList<>();
        this.foreignKeyColumns.forEach(relation -> 
            sentence.add(relation.sentence(lowerAll, schema))
        );
        return sentence;
    }

    /** ALTER TABLE table-name ADD CONSTRAINT key-name UNIQUE (index_col_name, ...); */
    public List<String> uniqueKeySentence(boolean lowerAll, Optional<String> schema) {
        ArrayList<String> sentence = new ArrayList<>();
        this.uniqueKeyColumns.forEach(constraint -> {
            String uniqueKey = toSentence("ALTER TABLE ", lowerAll) + schema.map(v -> v + ".").orElse("") + this.name
                + toSentence(" ADD CONSTRAINT ", lowerAll) + constraint.name
                + toSentence(" UNIQUE (", lowerAll) + constraint.columns.stream().map(column -> column.name).collect(Collectors.joining(", "))
                + ");"
            ;
            sentence.add(uniqueKey);
        });
        return sentence;
    }
    /** CREATE INDEX key-name ON table-name (index_col_name, ...); */
    public List<String> indexKeySentence(boolean lowerAll, Optional<String> schema) {
        ArrayList<String> sentence = new ArrayList<>();
        this.indexColumns.forEach(constraint -> {
            String indexKey = toSentence("CREATE INDEX ", lowerAll) + constraint.name
                + toSentence(" ON ", lowerAll) + schema.map(v -> v + ".").orElse("") + this.name
                + " (" + constraint.columns.stream().map(column -> column.name).collect(Collectors.joining(", "))
                + ");"
            ;
            sentence.add(indexKey);
        });
        return sentence;
    }

    public static String toSentence(String text, boolean lowerAll) {
        return lowerAll ? text.toLowerCase() : text;
    }

}
