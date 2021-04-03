# gradle-ER-plugin




## yaml形式

```yaml
version: 1.0.0

domains:
    # コメント
    ドメイン名: 型

commonColumns:
    <<カラム名>>: 
        logicalName: <<論理名>>
        info: <<カラム説明>>
        type: <<domainsの定義名 or VARCHAR とかの定義>>
        options: <<constraints>
        defaultValue: <<デフォルト値>>


tables:
    テーブル名:
        logicalName: <<論理名>>
        info: <<テーブル説明>>
        columns:
            # カラム名に対する補足
            <<カラム名>>:
                logicalName: <<論理名>>
                info: <<カラム説明>>
                type: <<domainsの定義名 or VARCHAR とかの定義>>
                options: <<constraints>
                defaultValue: <<デフォルト値>>
        pk:
            - <<カラム名>>
        fk:
            <<リレーション名>>:
                relate:
                    - <<親テーブルのカラム名>>
                to: 
                    <<子テーブル名>>:
                        - <<子テーブルのカラム名>>
        uq:
            <<ユニークキー名>>:
                - <<カラム名>>
        idx:
            <<インデックス名>>:
                - <<カラム名>>
```

## Extension形式

```
yamlER {
    // If you want to set encoding. 'windows' or 'linux' or 'mac'
    lineEnding = ''
    ddl {
        // specify yaml file
        yaml = file('.yml')
        // specify ddl output directory
        outDir = file('')
        // If you want to change ddl file name. default: yaml file name.
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
            driver = file()
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
