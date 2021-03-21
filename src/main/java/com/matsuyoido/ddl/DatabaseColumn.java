package com.matsuyoido.ddl;

import static com.matsuyoido.ddl.DatabaseTable.toSentence;
import java.util.Optional;

public class DatabaseColumn {

    /** 物理名 */
    final String name;
    /** domain定義 or 型 */
    final String type;

    /** 論理名 */
    private String logicalName;

    /** カラムコメント */
    private String comment;

    /** NotNull制約がある？ */
    private String constraints;

    /** デフォルト値 */
    private String defaultValue;

    public DatabaseColumn(String physicalName, String columnType) {
        this.name = physicalName;
        this.type = columnType;
    }

    /** 論理名設定 */
    public DatabaseColumn logicalName(String name) {
        this.logicalName = name;
        return this;
    }
    /** コメント設定 */
    public DatabaseColumn comment(String comment) {
        this.comment = comment;
        return this;
    }
    /** NotNull制約設定 */
    public DatabaseColumn constraints(String constraints) {
        this.constraints = constraints;
        return this;
    }
    /** デフォルト値設定 */
    public DatabaseColumn defaultValue(String value) {
        this.defaultValue = value;
        return this;
    }

    /** field-name type [constraints] [COMMENT "comment-string"]  */
    String createSentence(boolean lowerAll) {
        return this.name + " " + this.type
            + Optional.ofNullable(this.constraints).map(v -> " " + v).orElse("")
            + Optional.ofNullable(this.defaultValue).map(v -> {
                return toSentence(" DEFAULT ", lowerAll) + v;
              }).orElse("")
            + Optional.ofNullable(this.comment).map(v -> {
                String commentText = '"' + Optional.ofNullable(this.logicalName).map(n -> n + ":").orElse("") + v + '"';
                return toSentence(" COMMENT ", lowerAll) + commentText;
              }).orElse("")
        ;
    }

}
