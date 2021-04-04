# gradle-ER-plugin


## yaml format

```yaml
version: 1.0.0

# optional setting
domains:
    <<DomainName>>: <<column type>>

commonColumns:
    <<column name>>: 
        # optional setting
        logicalName: <<logical name>>
        # optional setting
        info: <<column comment>>
        # required setting
        type: <<domains.DomainName or column type>>
        # optional setting
        options: <<constraints>
        # optional setting
        defaultValue: <<default value>>


tables:
    <<table name>:
        # optional setting
        logicalName: <<logical name>>
        # optional setting
        info: <<table comment>>
        columns:
            <<column name>>:
                # optional setting
                logicalName: <<logical name>>
                # optional setting
                info: <<column comment>>
                # required setting
                type: <<domains.DomainName or column type>>
                # optional setting
                options: <<constraints>
                # optional setting
                defaultValue: <<default value>>
        # optional setting
        pk:
            - <<column name>>
        # optional setting
        fk:
            <<constraint name>>:
                relate:
                    - <<parent column>>
                to: 
                    <<children table name>>:
                        - <<child column>>
        # optional setting
        uq:
            <<constraint name>>:
                - <<column name>>
        # optional setting
        idx:
            <<constraint name>>:
                - <<column name>>
```

## Extension format

```
yamlER {
    // If you want to set encoding. 'windows' or 'linux' or 'mac'
    lineEnding = ''
    ddl {
        // specify yaml file
        yaml = file('.yml')
        // specify ddl output directory
        outDir = file('')
        // If you want to change ddl file name. default: ddl.sql
        fileName = ''
        // If you want to add schema name for all table name prefix .
        schema = ''
        // If set true, all table adding CREATE TABLE IF EXISTS.
        existCheck = false
        // If set true, DROP & TRUNCATE table before INSERT.
        truncate = false
        // If set true, all reserved word lower character.
        lowerAll  false
    }
    ddl {
        ...
    }
    er {
        // can specify schemaspy version.
        version = ''
        // specify database schema name.
        schema = ''
        // If you don't want to prepare database, specify ddl file.
        ddl = file('.sql')
        // specify er diagram output directory.
        outDir = file('')
        // If you want to connect exist database, setup under setting.
        db {
            // If you don't want to prepare database driver, specify driver version. But, only supprted for MySQL, MariaDB, PostgreSQL.
            version = ''
            // If you want to connect other than MySQL or MariaDB or PostgreSQL, specify database driver jar file.
            driver = file('.jar')
            // mysql, postgresql, mariadb, db2, and so on... ※ ref: https://github.com/schemaspy/schemaspy/tree/master/src/main/resources/org/schemaspy/types
            type = ''
            // Hostname/ip to connect to.
            host = ''
            // Port that database listens to.
            port = 0
            // Name of database to connect to.
            database = ''
            // Valid database user id with read access. 
            user = ''
            // Password associated with that user.
            password = ''
        }
    }
    er {
        ...
    }
}
```
