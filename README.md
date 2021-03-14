# gradle-ER-plugin

## 実装手順(予定)

1. gradle 経由で、 yaml ファイルを読み込む(テスト込み)
    - ※Extension を複数定義できる前提で組む
1. テーブル構造を定義するための、yaml の形式を決める
1. Extension の形式を考える
1. yaml の内容を使って、DDL 用のクラスを作成する
1. クラスから、 DDL を出力する
1. ==ここまでを1タスクとする==
1. schemaspy の jar がどのぐらい使えるかを試す
    - 参考
        - https://qiita.com/rh_taro/items/be9c2d4e53f8130bb140
        - https://mvnrepository.com/artifact/net.sourceforge.schemaspy/schemaspy
        - https://github.com/schemaspy/schemaspy
1. 使えそうなら、DDL を h2 データベースに流す()
1. h2データベースから、schemaspy でER図を生成する


## yaml形式

TODO

## Extension形式

TODO
