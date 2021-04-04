package com.matsuyoido.ddl;

import java.util.List;

public class DatabaseKey {

    /** Key名 */
    final String name;

    /** カラム */
    final List<DatabaseColumn> columns;

    public DatabaseKey(String keyName, List<DatabaseColumn> keyColumns) {
        this.name = keyName;
        this.columns = keyColumns;
    }


}
