package com.matsuyoido.plugin.er;

import java.io.File;
import java.io.Serializable;
import java.util.Optional;
import java.util.function.Predicate;

public class DDLExtension implements Serializable {
    private static final long serialVersionUID = 7893508411315218645L;

    File yaml;
    File outDir;
    String fileName;


    String schema;
    boolean existCheck = false;
    boolean truncate = false;
    boolean lowerAll = false;


    public File getYamlFile() {
        return this.yaml;
    }

    public File getOutputDir() {
        return this.outDir;
    }

    public Optional<String> getFileName() {
        return Optional.ofNullable(this.fileName).filter(Predicate.not(String::isBlank));
    }

    public boolean isIncludeExistCheck() {
        return this.existCheck;
    }

    public boolean isIncludeTruncateTable() {
        return this.truncate;
    }

    public boolean isAllCharacterLowerCase() {
        return this.lowerAll;
    }

}
