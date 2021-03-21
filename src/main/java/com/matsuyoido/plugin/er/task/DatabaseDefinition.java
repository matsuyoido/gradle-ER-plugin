package com.matsuyoido.plugin.er.task;

import java.util.List;
import java.util.Optional;

import com.matsuyoido.ddl.DatabaseTable;

public class DatabaseDefinition {

    final Optional<String> version;
    final List<DatabaseTable> tables;


    DatabaseDefinition(Optional<String> version, List<DatabaseTable> tables) {
        this.version = version;
        this.tables = tables;
    }

}
