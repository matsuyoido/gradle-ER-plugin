package com.matsuyoido.plugin.er;

import java.io.File;
import java.io.Serializable;
import java.util.Optional;
import java.util.function.Predicate;

public class ERExtension implements Serializable {
    private static final long serialVersionUID = 5026119737380019192L;

    String version;
    String schema;
    File ddl;
    File outDir;

    public Optional<String> getSchemaspyVersion() {
        return Optional.ofNullable(this.version).filter(Predicate.not(String::isBlank));
    }

    public Optional<String> getSchema() {
        return Optional.ofNullable(this.schema).filter(Predicate.not(String::isBlank));
    }

    public File getDDLFile() {
        return this.ddl;
    }

    public File getOutputDirectory() {
        return this.outDir;
    }

}
