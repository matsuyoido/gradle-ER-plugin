package com.matsuyoido.plugin.er.task;

import java.util.List;

class ForeignKeyInfo {
    String keyName;
    String parentTableName;
    List<String> parentRelationColumnNames;
    String childTableName;
    List<String> childRelationColumnNames;

    ForeignKeyInfo(String name, String parentTable, List<String> parentColumns, String childTable, List<String> childColumns) {
        this.keyName = name;
        this.parentTableName = parentTable;
        this.parentRelationColumnNames = parentColumns;
        this.childTableName = childTable;
        this.childRelationColumnNames = childColumns;
    }
}
