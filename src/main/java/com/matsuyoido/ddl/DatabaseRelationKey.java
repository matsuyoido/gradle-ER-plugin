package com.matsuyoido.ddl;

import static com.matsuyoido.ddl.DatabaseTable.toSentence;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DatabaseRelationKey {

    /** 外部キー名 */
    private final String name;

    /** 外部キー付与テーブル名 */
    private final String table;

    /** 親テーブルのカラム */
    private List<DatabaseColumn> columns;

    /** 子テーブル名 */
    final String relationTableName;
    /** 子テーブルカラム */
    private List<DatabaseColumn> childColumns;

    public DatabaseRelationKey(String keyName, String tableName, String childTableName) {
        this.name = keyName;
        this.table = tableName;
        this.relationTableName = childTableName;
    }

    void setColumns(List<DatabaseColumn> columns) {
        this.columns = columns;
    }

    void setChildColumns(List<DatabaseColumn> columns) {
        this.childColumns = columns;
    }

    /** ALTER TABLE table-name ADD CONSTRAINT key-name FOREIGN KEY(index_col_name, ...) REFERENCES table-name (index_col_name, ...); */
    String sentence(boolean lowerAll, Optional<String> schema) {
        return toSentence("ALTER TABLE ", lowerAll) + schema.map(v -> v + ".").orElse("") + this.table
            + toSentence(" ADD CONSTRAINT ", lowerAll) + this.name
            + toSentence(" FOREIGN KEY(", lowerAll) + this.columns.stream().map(column -> column.name).collect(Collectors.joining(", "))
            + toSentence(") REFERENCES ", lowerAll) + schema.map(v -> v + ".").orElse("") + this.relationTableName
            + " (" + this.childColumns.stream().map(column -> column.name).collect(Collectors.joining(", "))
            + ");"
        ;
    }

}
