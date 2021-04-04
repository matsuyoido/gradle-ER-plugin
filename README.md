# gradle-ER-plugin

## Requires

java equal to or greater than 11.

## Tasks

* ddl
* er

## Gradle Repository

https://plugins.gradle.org/plugin/com.matsuyoido.er

Latest: `1.0.3` or `1.0`

## yaml形式

```yaml
version: 1.0.0

domains:
    # コメント
    <<ドメイン名>>: <<型>>

commonColumns:
    <<カラム名>>: 
        logicalName: <<論理名>>
        info: <<カラム説明>>
        type: <<domainsのドメイン名 or VARCHAR とかの型定義>>
        options: <<制約、NotNullやCheck などの定義>
        defaultValue: <<デフォルト値>>


tables:
    <<テーブル名>>:
        logicalName: <<論理名>>
        info: <<テーブル説明>>
        columns:
            # カラム名に対する補足があればこのように書く
            <<カラム名>>:
                logicalName: <<論理名>>
                info: <<カラム説明>>
                type: <<domainsのドメイン名 or VARCHAR とかの型定義>>
                options: <<制約、NotNullやCheck などの定義>
                defaultValue: <<デフォルト値>>
        pk:
            - <<主キーのカラム名>>
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
    // 改行コード. 'windows' or 'linux' or 'mac'
    lineEnding = ''
    ddl {
        // DDLを生成したいYamlファイル
        yaml = file('.yml')
        // DDLファイルを出力したいフォルダ
        outDir = file('')
        // 設定がなければddl.sql。違う名前にしたい場合は設定する。
        fileName = ''
        // もしも全てのINSERT文などにスキーマ名をつけたい場合は設定する。
        schema = ''
        // true を設定したら、CREATE TABLE 文などに、 IF EXISTS をつける。
        existCheck = false
        // true を設定したら、 DROP文 や TRUNCATE文 を INSERT文の前に生成する。
        truncate = false
        // true を設定したら、 create table などの予約語を全て、小文字にする。
        lowerAll  false
    }
    ddl {
        ...
    }
    er {
        // schemaspyのバージョンを指定できる。指定しなければ、Gitで管理されているVersionの最新。
        version = ''
        // ER図を生成する対象のスキーマ名。
        schema = ''
        // 実際のデータベースを準備しないでER図だけが欲しい場合、ER図を生成する元となるDDLファイルを指定。実際のデータベースに接続する場合は不要。
        ddl = file('.sql')
        // ER図を出力するフォルダ。（専用のフォルダを指定すること推奨）
        outDir = file('')
        // 実際のデータベールに接続する場合は、下記の設定が必要。もしも ddl で設定しているなら、設定不要。
        db {
            // データベースドライバー のバージョン。MySQL, MariaDB, PostgreSQL であればドライバーのバージョンを指定すれば自動的にダウンロード。それ以外であれば、設定しない。
            version = ''
            // MySQL, MariaDB, PostgreSQL以外。もしくは、データベースドライバーを指定したい場合、そのドライバーのjarファイルを指定する必要がある。
            driver = file('.jar')
            // mysql, postgresql, mariadb, db2, などなど ※ 設定できるtypeは⇒ https://github.com/schemaspy/schemaspy/tree/master/src/main/resources/org/schemaspy/types
            type = ''
            // 接続するデータベースの ホストネーム or IPアドレス
            host = ''
            // 接続するデータベースの ポート番号
            port = 0
            // 接続するデータベースの データベース名(基本的には、スキーマ名と同じのことが多い)
            database = ''
            // 接続するデータベースを 読み込めるユーザー名
            user = ''
            // 接続するデータベースを 読み込めるユーザーのパスワード
            password = ''
        }
    }
    er {
        ...
    }
}
```

### 最低Extension設定

```
yamlER {
    // ddl タスク用
    ddl {
        yaml = file('test.yml')
        outDir = file("$rootDir/database/ddl")
    }
    // er タスク用
    er {
        schema = 'test_db'
        ddl = file("$rootDir/database/ddl/ddl.sql")
        outDir = file("$rootDir/database/er")
    }
}
```


## Tips

### 命名規則
1. 全て小文字のスネークケース
    - ex. table_name
    - ex. column_name
1. ローマ字は使わず、英語の名前にする
1. 複数形を利用しない
    * 複数形にした方が良いという意見も多いが、単数形の方が良い
    * 理由は2つ
        1. テーブルは各項目をグループ化してまとめたもので、実際のデータを表さないため
            - テーブル名を複数形にする人の主張は、「テーブルは複数のデータを持つでしょ？」
            - 私の反論は、「テーブル定義として、一つのデータグループとしてどのように構成されるのかを定義しているのに、複数にするの？」
            - ※Ruby を使っている場合は、この限りではないです。むしろ、複数形にしないと面倒な場合があるそうです。
        1. ORマッパーが自動的にテーブルのモデルを自動生成する時、クラス名がおかしくなる可能性がある
            + ex. jooq, DBFlute
            + users というテーブルだと、 `class Users {}` ができてしまう
1. 略語は一般的なもの以外は使わない
    * ISBNみたいに、略語の方が浸透しているなら利用した方が良い
        - ※ ISBN は、元々 「International Standard Book Number」
    * flag を flg など、意味が分からない略語なら使うべきではない(通じるかではなく、分かりやすいか)
1. カラム名に、自身を構成しているテーブル名をつけるべきではない
    * 参照の仕方は、 `テーブル名.カラム` というのがほとんどで `テーブル名.テーブルのカラム` だと冗長的な表現になってしまうため
    * 外部テーブルとの関連するカラムであれば、 `外部テーブル_id` のような命名の方がむしろ良い
1. カラム名に、 flag は使うべきではない
    * flag は、「true or false」が入るのが分かるだけで、どの状態なのかは分からない
        - 例外として、「public_flag」のように状態が分かりやすい場合もあるが、統一性も踏まえ避けるべき
    * 基本的には、「○○able」「○○_enable」「○○_available」「○○_used」のように、「trueなら○○の状態」というのが分かりやすいようにするべき
        - 「is_」や「has_」や、「exists_」を使いたくなるが、getter の補完は `isXxx()` がほとんどのため避けておいた方が良い。
1. カラムが時間を表す場合、「○○_datetime」とか「○○_date」は使うべきではない
    * 確かに、カラムの型は分かりやすいけれどそんなに分かりやすくしたい？(定義変わったら名前変えるの・・？)
    * それよりも、「○○_at」「○○_on」のような名前の方が英語の意味的にも良い
        - その時間・その瞬間を表す時、 `受動態_at`
            + ex. created_at , updated_at
        - 日付・曜日のように、特定の日を表す時、 `受動態_on`
            + ex. closed_on
    * 例外として、 英文法として利用されている場合はそれに合わせること


### 制約名

※これが良い！という根拠がありません…。下記はあくまでも一例です。このようにしているのは、ソートをした時にグループで見やすいからです。

* 外部キー: 親テーブル名_FK_子テーブル名XX
    - ex. user_FK_customer01
    - ※ user_FK_01 でも良いかもしれません。FK単体でどこの外部キーかを知れる方が便利ですが、テーブル名変更の工数と相談です。
* ユニーク制約: テーブル名_UQ_XX
    - ex. user_UQ_01
* インデックスキー: テーブル名_IDX_XX
    - ex. user_IDX_01


### Schemaspy のER図のデザインを変更したい場合

* gradle/plugin/
    - schemaspy jar file
    - layout/
        - schemaspy template files

gradle/plugin フォルダの配下に、schemaspy のjar ファイルをダウンロードする仕組みとなっています。

そのplugin フォルダの中に、 layout フォルダがあるかと思います。そこに、Schemaspy のファイルが全て入ってます(少しいじってますが)。

その中のファイルを、ご自由に変更することで、デザインを変更できます。

