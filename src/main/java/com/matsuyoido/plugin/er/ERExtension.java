package com.matsuyoido.plugin.er;

import java.io.File;
import java.io.Serializable;
import java.util.Optional;
import java.util.function.Predicate;

import org.gradle.api.Project;

import groovy.lang.Closure;

public class ERExtension implements Serializable {
    private static final long serialVersionUID = 5026119737380019192L;

    private final Project project;
    ERDbExtension db;
    String version;
    String schema;
    File ddl;
    File outDir;

    ERExtension(Project project) {
        this.project = project;
    }

    public void db(Closure<ERDbExtension> closure) {
        this.db = new ERDbExtension();
        this.project.configure(this.db, closure);
    }

    public ERDbExtension getDbSetting() {
        return this.db;
    }

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
