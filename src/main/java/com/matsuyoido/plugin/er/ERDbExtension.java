package com.matsuyoido.plugin.er;

import java.io.File;
import java.io.Serializable;
import java.util.Optional;
import java.util.function.Predicate;

public class ERDbExtension implements Serializable {
    private static final long serialVersionUID = 4113342844171420969L;

    String version;
    File driver;
    String type;
    String host;
    int port;
    String database;
    String user;
    String password;

    public Optional<String> getDatabaseVersion() {
        return Optional.ofNullable(this.version).filter(Predicate.not(String::isBlank));
    }

    public Optional<File> getDriverFile() {
        return Optional.ofNullable(this.driver);
    }

    public String getDatabaseType() {
        // https://github.com/schemaspy/schemaspy/tree/master/src/main/resources/org/schemaspy/types
        switch(this.type.toLowerCase()) {
            case "mysql":
                return "mysql";
            case "postgresql":
                return "pgsql";
            case "mariadb":
                return "mariadb";
            default:
                return this.type;
        }
    }

    public String getHostName() {
        return this.host;
    }
    public int getPort() {
        return this.port;
    }
    public String getDatabaseName() {
        return this.database;
    }
    public String getAccessUser() {
        return this.user;
    }
    public String getAccessPassword() {
        return this.password;
    }

    public boolean isNotEnoughSetting() {
        boolean isDriverFound = getDatabaseVersion().isPresent() || getDriverFile().filter(File::exists).isPresent();
        return !isDriverFound || isBlank(this.type) || isBlank(this.host) || isBlank(this.database) || isBlank(this.user) || isBlank(this.password);
    }
    private boolean isBlank(String val) {
        return val == null || val.isBlank();
    }

}
