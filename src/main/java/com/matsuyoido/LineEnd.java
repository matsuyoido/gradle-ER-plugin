package com.matsuyoido;

public enum LineEnd {
    PLATFORM(System.lineSeparator()),
    WINDOWS("\r\n"),
    LINUX("\n"),
    MAC("\r");

    private String line;
    LineEnd(String lineString) {
        this.line = lineString;
    }

    public String get() {
        return this.line;
    }

}
