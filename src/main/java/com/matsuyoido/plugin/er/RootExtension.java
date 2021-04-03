package com.matsuyoido.plugin.er;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.matsuyoido.LineEnd;

import org.gradle.api.Project;

import groovy.lang.Closure;

public class RootExtension implements Serializable {
    private static final long serialVersionUID = 7890587411115218645L;

    public RootExtension(Project project) {
        this.project = project;
    }

    private final Project project;
    private LineEnd lineEnd = LineEnd.PLATFORM;
    private List<DDLExtension> ddl = new ArrayList<>();
    private List<ERExtension> er = new ArrayList<>();

    public void ddl(Closure<DDLExtension> closure) {
        DDLExtension extension = new DDLExtension();
        this.project.configure(extension, closure);
        this.ddl.add(extension);
    }

    public void er(Closure<ERExtension> closure) {
        ERExtension extension = new ERExtension(this.project);
        this.project.configure(extension, closure);
        this.er.add(extension);
    }

    public void setLineEnding(String value) {
        if (value != null) {
            switch (value.toLowerCase()) {
                case "windows":
                    this.lineEnd = LineEnd.WINDOWS;
                    break;
                case "linux":
                    this.lineEnd = LineEnd.LINUX;
                    break;
                case "mac":
                    this.lineEnd = LineEnd.MAC;
                    break;
                default:
                    this.lineEnd = LineEnd.PLATFORM;
                    break;
            }
        }
    }


    public List<DDLExtension> getDDLConfig() {
        return Collections.unmodifiableList(this.ddl);
    }

    public List<ERExtension> getERConfig() {
        return Collections.unmodifiableList(this.er);
    }

    public LineEnd getLineEnd() {
        return this.lineEnd;
    }

}
