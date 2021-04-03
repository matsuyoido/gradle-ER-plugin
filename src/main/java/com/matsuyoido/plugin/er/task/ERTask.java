package com.matsuyoido.plugin.er.task;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.matsuyoido.plugin.er.ERDbExtension;
import com.matsuyoido.plugin.er.ERExtension;
import com.matsuyoido.plugin.er.RootExtension;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.JavaExec;
import org.h2.jdbcx.JdbcDataSource;

import groovy.json.JsonSlurper;

/**
 * ERTask
 */
public class ERTask extends JavaExec {
    private final String logFormat = "[ER] %s";
    private final Logger log = getProject().getLogger();
    private final Path schemaspyExecuteDir = getProject().getRootProject().getProjectDir().toPath().resolve("gradle/plugin");

    @Override
    public String getDescription() {
        return "DDL to ER.";
    }

    @Override
    public String getMain() {
        return "-jar";
    }

    @Override
    public void exec() {
        setWorkingDir(schemaspyExecuteDir.toFile());

        final RootExtension extension = getProject().getExtensions().getByType(RootExtension.class);
        extension.getERConfig()
                 .forEach(this::executeSchemaspy);
    }

    private void executeSchemaspy(ERExtension extension) {
        String schemaName = extension.getSchema().orElse("dbtest");
        boolean isH2Mode = extension.getDDLFile() != null;
        if (isH2Mode && !extension.getDDLFile().exists()) {
            log.error(String.format(logFormat, "ddl file not found: " + extension.getDDLFile().toPath()));
            throw new GradleException("ddl file not found.");
        }
        ERDbExtension databaseConnectExtension = extension.getDbSetting();
        if (!isH2Mode && (databaseConnectExtension == null || databaseConnectExtension.isNotEnoughSetting())) {
            log.error(String.format(logFormat, "extension setting is wrong."));
            log.warn(getExtensionRequiredSetting());
            throw new GradleException("db setting not found.");
        }

        String schemaspyJarPath = getSchemaspyJarPath(getVersion(extension.getSchemaspyVersion()));
        List<String> applicationArgs = new ArrayList<>();
        Runnable jarRun = () -> {
            setArgs(applicationArgs);
            super.exec();
        };

        applicationArgs.add(schemaspyJarPath);
        // https://schemaspy.readthedocs.io/en/latest/configuration/commandline.html
        applicationArgs.add("-vizjs");
        applicationArgs.add("-nopages");
        // output
        try {
            File outputDirectory = extension.getOutputDirectory();
            outputDirectory.mkdirs();
            applicationArgs.add("-o");
            applicationArgs.add(outputDirectory.getCanonicalPath());
        } catch (IOException e) {
            log.error(String.format(logFormat, "output directory setup error."), e);
            throw new GradleException(e.getMessage(), e);
        }
        setupTemplateFile(applicationArgs, schemaName);

        if (isH2Mode) {
            executeForH2ByMemory(schemaName, extension.getDDLFile().toPath(), applicationArgs, jarRun);
        } else {
            executeForConnectDatabase(schemaName, databaseConnectExtension, applicationArgs, jarRun);
        }
    }

    private void setupTemplateFile(List<String> applicationArgs, String schemaName) {
        Path templateFolderPath = schemaspyExecuteDir.resolve("layout");
        applicationArgs.add("-template");
        applicationArgs.add("." + File.separator + "layout");

        File templateFolder = templateFolderPath.toFile();
        if (templateFolder.exists()) {
            return;
        }
        try {
            log.lifecycle(String.format(logFormat, "try download template files."));
            templateFolder.mkdirs();
            List<String> layoutFilePathList = findLayoutFile();
            for (String layoutFilePath : layoutFilePathList) {
                Path filePath = templateFolderPath.resolve(layoutFilePath.replace("src/main/resources/layout/", ""));
                filePath.getParent().toFile().mkdirs();
                String downloadLayoutFile = "https://raw.githubusercontent.com/schemaspy/schemaspy/master/" + layoutFilePath;
                URL url = new URL(downloadLayoutFile);
                log.debug(String.format(logFormat, "[Start] template file download from [" + url + "]"));
                Files.copy(url.openStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                log.debug(String.format(logFormat, "[Complete] template file download: " + filePath));
            }
            log.lifecycle(String.format(logFormat, "template files download completed: " + templateFolder.getCanonicalFile()));
        } catch (IOException e) {
            log.error(String.format(logFormat, "download template file error."), e);
            throw new GradleException(e.getMessage(), e);
        }

        changeTemplateFile(templateFolderPath, schemaName);
    }

    private void executeForH2ByMemory(String schemaName, Path ddlFilePath, List<String> applicationArgs, Runnable jarExecutor) {
        org.h2.tools.Server server = null;
        try {
            org.h2.tools.Server tcpServer = org.h2.tools.Server.createTcpServer("-ifNotExists", "-tcpAllowOthers", "-baseDir", schemaspyExecuteDir.toString()).start();
            server = tcpServer;
            String h2url = server.getService().getURL() + "/" + schemaName;
            applicationArgs.add("-t");
            applicationArgs.add("h2");
            applicationArgs.add("-s");
            applicationArgs.add(schemaName);

            // set connect user
            applicationArgs.add("-u");
            applicationArgs.add("sa");

            applicationArgs.add("-db");
            applicationArgs.add(h2url);
            String driverPath = org.h2.Driver.class.getProtectionDomain()
                                                   .getCodeSource()
                                                   .getLocation()
                                                   .toURI()
                                                   .getPath();
            applicationArgs.add("-loadjars");
            applicationArgs.add(driverPath);
            applicationArgs.add("-dp");
            applicationArgs.add(driverPath);
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:" + h2url + ";DATABASE_TO_LOWER=TRUE" + ";INIT\\=CREATE SCHEMA IF NOT EXISTS " + schemaName + "\\;SET SCHEMA " + schemaName + ";");
            dataSource.setUser("sa");
            try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
                statement.execute(Files.readString(ddlFilePath));
            }
            jarExecutor.run();
        } catch (URISyntaxException e) {
            log.error(String.format(logFormat, "load h2 driver error."), e);
            throw new GradleException(e.getMessage(), e);
        } catch (SQLException | IOException e) {
            log.error(String.format(logFormat, "setup database error."), e);
            throw new GradleException(e.getMessage(), e);
        } finally {
            if (server != null) {
                server.stop();
                schemaspyExecuteDir.resolve(schemaName + ".mv.db").toFile().delete();
            }
        }
    }

    private void executeForConnectDatabase(String schemaName, ERDbExtension databaseConnectExtension, List<String> applicationArgs, Runnable jarExecutor) {
        applicationArgs.add("-t");
        applicationArgs.add(databaseConnectExtension.getDatabaseType());
        applicationArgs.add("-db");
        applicationArgs.add(databaseConnectExtension.getDatabaseName());
        applicationArgs.add("-s");
        applicationArgs.add(schemaName);
        applicationArgs.add("-host");
        applicationArgs.add(databaseConnectExtension.getHostName());
        applicationArgs.add("-port");
        applicationArgs.add(String.valueOf(databaseConnectExtension.getPort()));
        applicationArgs.add("-u");
        applicationArgs.add(databaseConnectExtension.getAccessUser());
        applicationArgs.add("-p");
        applicationArgs.add(databaseConnectExtension.getAccessPassword());
        applicationArgs.add("-loadjars");
        String driverPath = getDriverJarPath(databaseConnectExtension);
        applicationArgs.add(driverPath);
        applicationArgs.add("-dp");
        applicationArgs.add(driverPath);
        jarExecutor.run();
    }

    @SuppressWarnings("unchecked")
    private String getVersion(Optional<String> version) {
        return version.orElseGet(() -> {
            String schemaspyGitInfoUrl = "https://api.github.com/repos/schemaspy/schemaspy/releases/latest";
            try {
                URL url = new URL(schemaspyGitInfoUrl);
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                Map<String, List<String>> header = http.getHeaderFields();
                while (isRedirected(header)) {
                    URL redirectUrl = new URL(header.get("Location").get(0));
                    http = (HttpURLConnection) redirectUrl.openConnection();
                    header = http.getHeaderFields();
                }
                try (BufferedInputStream input = new BufferedInputStream(http.getInputStream());
                        InputStreamReader reader = new InputStreamReader(input);
                        BufferedReader output = new BufferedReader(reader)) {
                    String text = output.lines().collect(Collectors.joining());
                    Map<String, String> jsonData = (Map<String, String>) new JsonSlurper().parseText(text);
                    String tagName = jsonData.get("tag_name");
                    return tagName.startsWith("v") ? tagName.split("v")[1] : tagName;
                }
            } catch (IOException e) {
                log.error(String.format(logFormat, "version check error."), e);
                throw new GradleException(e.getMessage(), e);
            }
        });

    }

    private boolean isRedirected(Map<String, List<String>> header) {
        for (String hv : header.get(null)) {
            if (hv.contains(" 301 ") || hv.contains(" 302 ")) {
                return true;
            }
        }
        return false;
    }

    private String getSchemaspyJarPath(String version) {
        log.lifecycle(String.format(logFormat, "use schemaspy version: " + version));
        File jarFile = schemaspyExecuteDir.resolve("schemaspy-" + version + ".jar").toFile();
        try {
            if (!jarFile.exists()) {
                jarFile.mkdirs();
                jarFile.createNewFile();
                log.lifecycle(String.format(logFormat, "schemaspy jar not found."));
                String downloadJarUrl = "https://github.com/schemaspy/schemaspy/releases/download/v" + version + "/schemaspy-" + version + ".jar";
                log.lifecycle(String.format(logFormat, "try download schemaspy jar from " + downloadJarUrl));
                URL url = new URL(downloadJarUrl);
                Files.copy(url.openStream(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.lifecycle(String.format(logFormat, "schemaspy jar download completed: " + jarFile.getCanonicalPath()));
            }
            return jarFile.getCanonicalPath();
        } catch (FileNotFoundException e) {
            log.error(String.format(logFormat, "schemaspy version not supported: " + version));
            log.lifecycle(String.format(logFormat, "check -> https://github.com/schemaspy/schemaspy/releases"));
            throw new GradleException(e.getMessage(), e);
        } catch (IOException e) {
            log.error(String.format(logFormat, "jar file get error."), e);
            throw new GradleException(e.getMessage(), e);
        }
    }

    private void changeTemplateFile(Path templateFolderPath, String schemaName) {
        try {
            Files.writeString(templateFolderPath.resolve("container.html"), String.join(System.lineSeparator(),
            "<!DOCTYPE html>"
            ,"<html>"
            ,"    <head>"
            ,"        <meta charset='utf-8'>"
            ,"        <meta http-equiv='X-UA-Compatible' content='IE=edge'>"
            ,"        <title>" + schemaName + " ER</title>"
            ,"        <meta content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no' name='viewport'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/admin-lte/bootstrap/css/bootstrap.min.css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/font-awesome/css/font-awesome.min.css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/ionicons/css/ionicons.min.css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/datatables.net-bs/css/dataTables.bootstrap.min.css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/datatables.net-buttons-bs/css/buttons.bootstrap.min.css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/codemirror/codemirror.css'>"
            ,"        <link href='{{rootPath}}fonts/indieflower/indie-flower.css' rel='stylesheet' type='text/css'>"
            ,"        <link href='{{rootPath}}fonts/source-sans-pro/source-sans-pro.css' rel='stylesheet' type='text/css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/admin-lte/dist/css/AdminLTE.min.css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/salvattore/salvattore.css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}bower/admin-lte/dist/css/skins/_all-skins.min.css'>"
            ,"        <link rel='stylesheet' href='{{rootPath}}schemaSpy.css'>"
            ,"        <!--[if lt IE 9]>"
            ,"        <script src='{{rootPath}}bower/html5shiv/html5shiv.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/respond/respond.min.js'></script>"
            ,"        <![endif]-->"
            ,"    </head>"
            ,"    <body class='hold-transition skin-blue layout-top-nav'>"
            ,"        <div class='wrapper'>"
            ,"            {{#isMultipleSchemas}}"
            ,"            {{/isMultipleSchemas}}"
            ,"            {{^isMultipleSchemas}}"
            ,"            <header class='main-header'>"
            ,"                <nav class='navbar navbar-static-top'>"
            ,"                    <div class='container'>"
            ,"                        <div class='navbar-header'>"
            ,"                            <a href='{{rootPathtoHome}}index.html' class='navbar-brand'><b>" + schemaName + "</b></a>"
            ,"                            <button type='button' class='navbar-toggle collapsed' data-toggle='collapse' data-target='#navbar-collapse'><i class='fa fa-bars'></i></button>"
            ,"                        </div>"
            ,"                        <div class='collapse navbar-collapse pull-left' id='navbar-collapse'>"
            ,"                            <ul class='nav navbar-nav'>"
            ,"                                <li><a href='{{rootPath}}index.html'>Tables <span class='sr-only'>(current)</span></a></li>"
            ,"                                <li><a href='{{rootPath}}columns.html' title='All of the columns in the schema'>Columns</a></li>"
            ,"                                <li><a href='{{rootPath}}constraints.html' title='Useful for diagnosing error messages that just give constraint name or number'>Constraints</a></li>"
            ,"                                <li><a href='{{rootPath}}relationships.html' title='Diagram of table relationships'>Relationships</a></li>"
            ,"                                <li><a href='{{rootPath}}orphans.html' title='View of tables with neither parents nor children'>Orphan&nbsp;Tables</a></li>"
            ,"                                <li><a href='{{rootPath}}anomalies.html' title='Things that might not be quite right'>Anomalies</a></li>"
            ,"                                <li><a href='{{rootPath}}routines.html' title='Procedures and functions'>Routines</a></li>"
            ,"                            </ul>"
            ,"                        </div>"
            ,"                    </div>"
            ,"                </nav>"
            ,"            </header>"
            ,"            {{/isMultipleSchemas}}"
            ,"            <!-- Main content -->"
            ,"            <!-- Full Width Column -->"
            ,"            <div class='content-wrapper'>"
            ,"                {{{content}}}"
            ,"            </div>"
            ,"            <!-- /.content-wrapper -->"
            ,"            <footer class='main-footer'>"
            ,"                <div class='text-right'>"
            ,"                    <div class='hidden-xs'>"
            ,"                        Generated by <a href='http://schemaspy.org/' class='logo-text'><i class='fa fa-database'></i> SchemaSpy @project.version@</a>"
            ,"                    </div>"
            ,"                </div>"
            ,"                <!-- /.container -->"
            ,"            </footer>"
            ,"        </div>"
            ,"        <!-- ./wrapper -->"
            ,"        <script src='{{rootPath}}bower/admin-lte/plugins/jQuery/jquery-2.2.3.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/admin-lte/plugins/jQueryUI/jquery-ui.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/admin-lte/bootstrap/js/bootstrap.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/datatables.net/jquery.dataTables.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/datatables.net-bs/js/dataTables.bootstrap.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/datatables.net-buttons/dataTables.buttons.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/datatables.net-buttons-bs/js/buttons.bootstrap.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/datatables.net-buttons/buttons.html5.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/datatables.net-buttons/buttons.print.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/datatables.net-buttons/buttons.colVis.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/js-xlsx/xlsx.full.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/pdfmake/pdfmake.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/pdfmake/vfs_fonts.js'></script>"
            ,"        <script src='{{rootPath}}bower/admin-lte/plugins/slimScroll/jquery.slimscroll.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/admin-lte/plugins/fastclick/fastclick.js'></script>"
            ,"        <script src='{{rootPath}}bower/salvattore/salvattore.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/anchor-js/anchor.min.js'></script>"
            ,"        <script src='{{rootPath}}bower/codemirror/codemirror.js'></script>"
            ,"        <script src='{{rootPath}}bower/codemirror/sql.js'></script>"
            ,"        <script src='{{rootPath}}bower/admin-lte/dist/js/app.min.js'></script>"
            ,"        <script src='{{pageScript}}'></script>"
            ,"        <script src='{{rootPath}}schemaSpy.js'></script>"
            ,"    </body>"
            ,"</html>"
            ));

            Files.writeString(templateFolderPath.resolve("main.html"), String.join(System.lineSeparator(),
            "                <section class='content-header'>"
            ,"                    <h1>Tables <small>Generated on {{database.connectTime}}</small></h1><br />"
            ,"                    {{#description}}"
            ,"                    <div class='row'>"
            ,"                        <div class='col-md-12'>"
            ,"                            <div class='box box-solid'>"
            ,"                                <div class='box-body'>"
            ,"                                    <p>{{{description}}}</p>"
            ,"                                </div>"
            ,"                            </div>"
            ,"                        </div>"
            ,"                    </div>"
            ,"                    {{/description}}"
            ,"                    <a href='insertionOrder.txt' title='Useful for loading data into a database'>Insertion Order</a>"
            ,"                    <a href='deletionOrder.txt' title='Useful for purging data from a database'>Deletion Order</a>"
            ,"                </section>"
            ,"                <!-- Main content -->"
            ,"                <section class='content'>"
            ,"                    <div class='row'>"
            ,"                        <div class='col-md-2 col-sm-4 col-xs-12'>"
            ,"                            <div class='info-box'>"
            ,"                                <span class='info-box-icon bg-aqua'><i class='fa fa-table'></i></span>"
            ,"                                <div class='info-box-content'>"
            ,"                                    <span class='info-box-text'>TABLES</span>"
            ,"                                    <span class='info-box-number'>{{tablesAmount}}</span>"
            ,"                                </div>"
            ,"                                <!-- /.info-box-content -->"
            ,"                            </div>"
            ,"                            <!-- /.info-box -->"
            ,"                        </div>"
            ,"                        <!-- /.col -->"
            ,"                        <div class='col-md-2 col-sm-4 col-xs-12'>"
            ,"                            <div class='info-box'>"
            ,"                                <span class='info-box-icon bg-teal'><i class='fa fa-table'></i></span>"
            ,"                                <div class='info-box-content'>"
            ,"                                    <span class='info-box-text'>VIEWS</span>"
            ,"                                    <span class='info-box-number'>{{viewsAmount}}</span>"
            ,"                                </div>"
            ,"                                <!-- /.info-box-content -->"
            ,"                            </div>"
            ,"                            <!-- /.info-box -->"
            ,"                        </div>"
            ,"                        <!-- /.col -->"
            ,"                        <div class='col-md-2 col-sm-4 col-xs-12'>"
            ,"                            <div class='info-box'>"
            ,"                                <span class='info-box-icon bg-green'><span class='glyphicon glyphicon-list-alt' aria-hidden='true'></span></span>"
            ,"                                <div class='info-box-content'>"
            ,"                                    <span class='info-box-text'>COLUMNS</span>"
            ,"                                    <span class='info-box-number'>{{columnsAmount}}</span>"
            ,"                                </div>"
            ,"                                <!-- /.info-box-content -->"
            ,"                            </div>"
            ,"                            <!-- /.info-box -->"
            ,"                        </div>"
            ,"                        <!-- /.col -->"
            ,"                        <div class='col-md-2 col-sm-4 col-xs-12'>"
            ,"                            <div class='info-box'>"
            ,"                                <span class='info-box-icon bg-yellow'><i class='ion ion-key'></i></span>"
            ,"                                <div class='info-box-content'>"
            ,"                                    <span class='info-box-text'>Constraints</span>"
            ,"                                    <span class='info-box-number'>{{constraintsAmount}}</span>"
            ,"                                </div>"
            ,"                                <!-- /.info-box-content -->"
            ,"                            </div>"
            ,"                            <!-- /.info-box -->"
            ,"                        </div>"
            ,"                        <!-- /.col -->"
            ,"                        <div class='col-md-2 col-sm-4 col-xs-12'>"
            ,"                            <div class='info-box'>"
            ,"                                <span class='info-box-icon bg-red-active'><i class='fa fa-question' aria-hidden='true'></i></span>"
            ,"                                <div class='info-box-content'>"
            ,"                                    <span class='info-box-text'>Anomalies</span>"
            ,"                                    <span class='info-box-number'>{{anomaliesAmount}}</span>"
            ,"                                </div>"
            ,"                                <!-- /.info-box-content -->"
            ,"                            </div>"
            ,"                            <!-- /.info-box -->"
            ,"                        </div>"
            ,"                        <!-- /.col -->"
            ,"                        <div class='col-md-2 col-sm-4 col-xs-12'>"
            ,"                            <div class='info-box'>"
            ,"                                <span class='info-box-icon bg-navy'><i class='fa fa-file-code-o' aria-hidden='true'></i></span>"
            ,"                                <div class='info-box-content'>"
            ,"                                    <span class='info-box-text'>Routines</span>"
            ,"                                    <span class='info-box-number'>{{routinesAmount}}</span>"
            ,"                                </div>"
            ,"                                <!-- /.info-box-content -->"
            ,"                            </div>"
            ,"                            <!-- /.info-box -->"
            ,"                        </div>"
            ,"                        <!-- /.col -->"
            ,"                    </div>"
            ,"                    {{#catalog}}"
            ,"                    {{#catalog.comment}}"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <h3 class='box-title'>Catalog {{catalog.name}}</h3>"
            ,"                            <span class='label label-primary pull-right'><i class='fa fa-cog fa-2x'></i></span>"
            ,"                        </div><!-- /.box-header -->"
            ,"                        <div class='box-body'>"
            ,"                            <p> {{{catalog.comment}}} </p>"
            ,"                        </div><!-- /.box-body -->"
            ,"                    </div>"
            ,"                    {{/catalog.comment}}"
            ,"                    {{/catalog}}"
            ,"                    {{#schema}}"
            ,"                    {{#schema.comment}}"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <h3 class='box-title'>Schema {{schema.name}}</h3>"
            ,"                            <span class='label label-primary pull-right'><i class='fa fa-cog fa-2x'></i></span>"
            ,"                        </div><!-- /.box-header -->"
            ,"                        <div class='box-body'>"
            ,"                            <p> {{{schema.comment}}} </p>"
            ,"                        </div><!-- /.box-body -->"
            ,"                    </div>"
            ,"                    {{/schema.comment}}"
            ,"                    {{/schema}}"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <h3 class='box-title'>Tables</h3>"
            ,"                            <span class='label label-primary pull-right'><i class='fa fa-database fa-2x'></i></span>"
            ,"                        </div><!-- /.box-header -->"
            ,"                        <div class='box-body'>"
            ,"                            <table id='database_objects' class='table table-bordered table-striped dataTable' role='grid'>"
            ,"                                <thead>"
            ,"                                    <tr>"
            ,"                                        <th valign='bottom'>Table / View</th>"
            ,"                                        <th align='right' valign='bottom'>Children</th>"
            ,"                                        <th align='right' valign='bottom'>Parents</th>"
            ,"                                        <th align='right' valign='bottom'>Columns</th>"
            ,"                                        {{#displayNumRows}}"
            ,"                                        <th align='right' valign='bottom'>Rows</th>"
            ,"                                        {{/displayNumRows}}"
            ,"                                        <th align='right' valign='bottom'>Type</th>"
            ,"                                        <th class='toggle'>Comments</th>"
            ,"                                    </tr>"
            ,"                                </thead>"
            ,"                                <tbody>"
            ,"                                    {{#tables}}"
            ,"                                    <tr class='tbl even' valign='top'>"
            ,"                                        <td class='detail'><a href='tables/{{table.name}}.html'>{{table.name}}</a></td>"
            ,"                                        <td class='detail' align='right'>{{table.maxChildren}}</td>"
            ,"                                        <td class='detail' align='right'>{{table.maxParents}}</td>"
            ,"                                        <td class='detail' align='right'>{{table.columns.size}}</td>"
            ,"                                        {{#displayNumRows}}"
            ,"                                        <td class='detail' align='right'>{{table.numRows}}</td>"
            ,"                                        {{/displayNumRows}}"
            ,"                                        <td class='detail' align='right'>{{table.type}}</td>"
            ,"                                        <td class='comment detail' style='display: table-cell;'>{{{comments}}}</td>"
            ,"                                    </tr>"
            ,"                                    {{/tables}}"
            ,"                            </table>"
            ,"                        </div>"
            ,"                    </div><!-- /.box-body -->"
            ,"                </section>"
            ,"                <!-- /.content -->"
            ,"                <script>"
            ,"                    var config = {"
            ,"                        pagination: {{paginationEnabled}}"
            ,"                    }"
            ,"                </script>"
            ));

            Files.writeString(templateFolderPath.resolve("constraint.html"), String.join(System.lineSeparator(),
            "                <section class='content-header'>"
            ,"                    <h1>Constraints</h1>"
            ,"                </section>"
            ,"                <!-- Main content -->"
            ,"                <section class='content'>"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <i class='ion ion-key'></i>"
            ,"                            <h3 class='box-title'>{{constraints.size}} Foreign Key Constraints</h3>"
            ,"                            <div class='box-tools pull-right'>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='collapse'><i class='fa fa-minus'></i></button>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='remove'><i class='fa fa-times'></i></button>"
            ,"                            </div>"
            ,"                        </div>"
            ,"                        <div class='box-body'>"
            ,"                            <table id='fk_table' class='table table-bordered table-striped dataTable' role='grid'>"
            ,"                                <thead align='left'>"
            ,"                                    <tr>"
            ,"                                        <th>Constraint Name</th>"
            ,"                                        <th>Child Column</th>"
            ,"                                        <th>Parent Column</th>"
            ,"                                        <th>Delete Rule</th>"
            ,"                                    </tr>"
            ,"                                </thead>"
            ,"                                <tbody>"
            ,"                                    {{#constraints}}"
            ,"                                    <tr>"
            ,"                                        <td>{{name}}</td>"
            ,"                                        <td>"
            ,"                                            <table border='0' cellspacing='0' cellpadding='0'>"
            ,"                                                {{#childColumns}}"
            ,"                                                <tr>"
            ,"                                                    <td><a href='tables/{{table.name}}.html'>{{table.name}}</a><span>.{{name}}</span></td>"
            ,"                                                </tr>"
            ,"                                                {{/childColumns}}"
            ,"                                            </table>"
            ,"                                        </td>"
            ,"                                        <td>"
            ,"                                            <table border='0' cellspacing='0' cellpadding='0'>"
            ,"                                                {{#parentColumns}}"
            ,"                                                <tr>"
            ,"                                                    <td><a href='tables/{{table.name}}.html'>{{table.name}}</a><span>.{{name}}</span></td>"
            ,"                                                </tr>"
            ,"                                                {{/parentColumns}}"
            ,"                                            </table>"
            ,"                                        </td>"
            ,"                                        <td><span title='{{deleteRuleDescription}}'>{{deleteRuleName}}</span></td>"
            ,"                                    </tr>"
            ,"                                    {{/constraints}}"
            ,"                                </tbody>"
            ,"                            </table>"
            ,"                        </div>"
            ,"                    </div>"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <i class='fa fa-sitemap'></i>"
            ,"                            <h3 class='box-title'>Check Constraints</h3>"
            ,"                            <div class='box-tools pull-right'>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='collapse'><i class='fa fa-minus'></i></button>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='remove'><i class='fa fa-times'></i></button>"
            ,"                            </div>"
            ,"                        </div>"
            ,"                        <div class='box-body'>"
            ,"                            <table id='check_table' class='table table-bordered table-striped dataTable' role='grid'>"
            ,"                                <thead align='left'>"
            ,"                                    <tr>"
            ,"                                        <th>Table</th>"
            ,"                                        <th>Constraint Name</th>"
            ,"                                        <th>Constraint</th>"
            ,"                                    </tr>"
            ,"                                </thead>"
            ,"                                <tbody>"
            ,"                                    {{#checkConstraints}}"
            ,"                                    <tr>"
            ,"                                        <td><a href='tables/{{tableName}}.html'>{{tableName}}</a></td>"
            ,"                                        <td>{{name}}</td>"
            ,"                                        <td>{{definition}}</td>"
            ,"                                    </tr>"
            ,"                                    {{/checkConstraints}}"
            ,"                                </tbody>"
            ,"                            </table>"
            ,"                        </div>"
            ,"                    </div>"
            ,"                </section>"
            ,"                <script>"
            ,"                    var config = {"
            ,"                        pagination: {{paginationEnabled}}"
            ,"                    }"
            ,"                </script>"
            ));

            Files.writeString(templateFolderPath.resolve("tables/table.html"), String.join(System.lineSeparator(),
            "                <section class='content-header'>"
            ,"                    <h1>{{table.name}}</h1>{{#displayNumRows}}<p><span id='recordNumber'>{{table.numRows}}</span> rows</p>{{/displayNumRows}}<br />"
            ,"                    {{#comments}}"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <i class='fa fa-file-text-o'></i>"
            ,"                            <h3 id='Description' class='box-title'>Description</h3>"
            ,"                            <div class='box-tools pull-right'>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='collapse'><i class='fa fa-minus'></i></button>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='remove'><i class='fa fa-times'></i></button>"
            ,"                            </div>"
            ,"                        </div><!-- /.box-header -->"
            ,"                        <div class='box-body clearfix'>"
            ,"                            {{{comments}}}"
            ,"                        </div><!-- /.box-body -->"
            ,"                    </div>"
            ,"                    {{/comments}}"
            ,"                </section>"
            ,"                <!-- Main content -->"
            ,"                <section class='content'>"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <span class='glyphicon glyphicon-list-alt' aria-hidden='true'></span>"
            ,"                            <h3 id='Columns' class='box-title'>Columns</h3>"
            ,"                            <div class='box-tools pull-right'>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='collapse'><i class='fa fa-minus'></i></button>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='remove'><i class='fa fa-times'></i></button>"
            ,"                            </div>"
            ,"                        </div>"
            ,"                        <div class='box-body'>"
            ,"                            <table id='standard_table' class='table table-bordered table-striped dataTable' role='grid'>"
            ,"                                <thead align='left'>"
            ,"                                <tr>"
            ,"                                    <th>Column</th>"
            ,"                                    <th>Type</th>"
            ,"                                    <th>Size</th>"
            ,"                                    <th title='Are nulls allowed?'>Nulls</th>"
            ,"                                    <th title='Is column automatically updated?'>Auto</th>"
            ,"                                    <th title='Default value'>Default</th>"
            ,"                                    <th title='Columns in tables that reference this column'>Children</th>"
            ,"                                    <th title='Columns in tables that are referenced by this column'>Parents</th>"
            ,"                                    <th title='Comments' class='toggle'><span>Comments</span></th>"
            ,"                                </tr>"
            ,"                                </thead>"
            ,"                                <tbody>"
            ,"                                {{#columns}}"
            ,"                                <tr>"
            ,"                                    <td{{{key}}}>{{{keyIcon}}}<span id='{{column.name}}'>{{column.name}}</span></td>"
            ,"                                    <td>{{column.typeName}}</td>"
            ,"                                    <td>{{column.detailedSize}}</td>"
            ,"                                    <td title='{{titleNullable}}'>{{nullable}}</td>"
            ,"                                    <td title='{{titleAutoUpdated}}'>{{autoUpdated}}</td>"
            ,"                                    <td>{{defaultValue}}</td>"
            ,"                                    <td>"
            ,"                                        <table border='0' cellspacing='0' cellpadding='0'>"
            ,"                                            {{#children}}"
            ,"                                            <tr>"
            ,"                                                <td title='{{constraint}}'><a href='{{path}}{{table.name}}.html'>{{table.name}}</a><span class='relatedKey'>.{{column.name}}</span></td>"
            ,"                                                <td class='constraint detail'>{{constraint.name}}<span title='{{constraint.deleteRuleDescription}}'>{{constraint.deleteRuleAlias}}</span></td>"
            ,"                                            </tr>"
            ,"                                            {{/children}}"
            ,"                                        </table>"
            ,"                                    </td>"
            ,"                                    <td>"
            ,"                                        <table border='0' cellspacing='0' cellpadding='0'>"
            ,"                                            {{#parents}}"
            ,"                                            <tr>"
            ,"                                                <td title='{{constraint}}'><a href='{{path}}{{table.name}}.html'>{{table.name}}</a><span class='relatedKey'>.{{column.name}}</span></td>"
            ,"                                                <td class='constraint detail'>{{constraint.name}}<span title='{{constraint.deleteRuleDescription}}'>{{constraint.deleteRuleAlias}}</span></td>"
            ,"                                            </tr>"
            ,"                                            {{/parents}}"
            ,"                                        </table>"
            ,"                                    </td>"
            ,"                                    <td>{{{comments}}}</td>"
            ,"                                </tr>"
            ,"                                {{/columns}}"
            ,"                                </tbody>"
            ,"                            </table>"
            ,"                        </div>"
            ,"                    </div>"
            ,"                    {{^indexes.isEmpty}}"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <i class='fa fa-sitemap'></i>"
            ,"                            <h3 id='Indexes' class='box-title'>Indexes</h3>"
            ,"                            <div class='box-tools pull-right'>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='collapse'><i class='fa fa-minus'></i></button>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='remove'><i class='fa fa-times'></i></button>"
            ,"                            </div>"
            ,"                        </div>"
            ,"                        <div class='box-body'>"
            ,"                            <table id='indexes_table' class='table table-bordered table-striped dataTable' role='grid'>"
            ,"                                <thead>"
            ,"                                <tr>"
            ,"                                    <th>Constraint Name</th>"
            ,"                                    <th>Type</th>"
            ,"                                    <th>Sort</th>"
            ,"                                    <th>Column(s)</th>"
            ,"                                </tr>"
            ,"                                </thead>"
            ,"                                <tbody>"
            ,"                                {{#indexes}}"
            ,"                                <tr>"
            ,"                                    <td{{{key}}}>{{{keyIcon}}}{{index.name}}</td>"
            ,"                                    <td>{{index.type}}</td>"
            ,"                                    <td>{{{index.sortAsString}}}</td>"
            ,"                                    <td>{{index.columnsAsString}}</td>"
            ,"                                </tr>"
            ,"                                {{/indexes}}"
            ,"                                </tbody>"
            ,"                            </table>"
            ,"                        </div><!-- /.box-body -->"
            ,"                    </div>"
            ,"                    {{/indexes.isEmpty}}"
            ,"                    {{^checkConstraints.isEmpty}}"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <i class='fa fa-sitemap'></i>"
            ,"                            <h3 class='box-title'>Check Constraints</h3>"
            ,"                            <div class='box-tools pull-right'>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='collapse'><i class='fa fa-minus'></i></button>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='remove'><i class='fa fa-times'></i></button>"
            ,"                            </div>"
            ,"                        </div>"
            ,"                        <div class='box-body'>"
            ,"                            <table id='check_table' class='table table-bordered table-striped dataTable' role='grid'>"
            ,"                                <thead align='left'>"
            ,"                                <tr>"
            ,"                                    <th>Constraint Name</th>"
            ,"                                    <th>Constraint</th>"
            ,"                                </tr>"
            ,"                                </thead>"
            ,"                                <tbody>"
            ,"                                {{#checkConstraints}}"
            ,"                                <tr>"
            ,"                                    <td>{{name}}</td>"
            ,"                                    <td>{{definition}}</td>"
            ,"                                </tr>"
            ,"                                {{/checkConstraints}}"
            ,"                                </tbody>"
            ,"                            </table>"
            ,"                        </div>"
            ,"                    </div>"
            ,"                    {{/checkConstraints.isEmpty}}"
            ,"                    {{^diagrams.isEmpty}}"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <i class='fa fa-code-fork'></i>"
            ,"                            <h3 id='Relationships' class='box-title'>Relationships</h3>"
            ,"                            <div class='box-tools pull-right'>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='collapse'><i class='fa fa-minus'></i></button>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='remove'><i class='fa fa-times'></i></button>"
            ,"                            </div>"
            ,"                        </div>"
            ,"                        <div class='box-body'>"
            ,"                            <div class='nav-tabs-custom'><!-- Tabs within a box -->"
            ,"                                <h5>Close relationships within degrees of separation</h5>"
            ,"                                <ul class='nav nav-tabs pull-left ui-sortable-handle'>"
            ,"                                    {{#diagrams}}"
            ,"                                    <li class='{{active}}'><a href='#{{id}}-chart' data-toggle='tab' aria-expanded='true'>{{name}}</a></li>"
            ,"                                    {{/diagrams}}"
            ,"                                </ul>"
            ,"                                <div class='tab-content no-padding'>"
            ,"                                    {{#diagrams}}"
            ,"                                    <div class='chart tab-pane {{active}}' id='{{id}}-chart' style='position: relative; overflow-x:auto;'>"
            ,"                                        {{{map}}}"
            ,"                                        {{#isEmbed}}"
            ,"                                        <a name='diagram'><object type='image/svg+xml' id='{{id}}' data='../diagrams/tables/{{fileName}}' class='diagram' border='0' align='left'></object></a>"
            ,"                                        {{/isEmbed}}"
            ,"                                        {{^isEmbed}}"
            ,"                                        <a name='diagram'><img id='{{id}}' src='../diagrams/tables/{{fileName}}' usemap='#{{mapName}}' class='diagram' border='0' align='left'></a>"
            ,"                                        {{/isEmbed}}"
            ,"                                    </div>"
            ,"                                    {{/diagrams}}"
            ,"                                </div>"
            ,"                            </div>"
            ,"                        </div><!-- /.box-body -->"
            ,"                    </div>"
            ,"                    {{/diagrams.isEmpty}}"
            ,"                    {{#diagrams.isEmpty}}{{^table.isView}}"
            ,"                    <div class='alert alert-warning alert-dismissible'>"
            ,"                        <button type='button' class='close' data-dismiss='alert' aria-hidden='true'>×</button>"
            ,"                        <h4><i class='icon fa fa-warning'></i>Diagram producer might be missing</h4>"
            ,"                        <p>No diagrams were produced please see application output for any errors</p>"
            ,"                    </div>"
            ,"                    {{/table.isView}}{{/diagrams.isEmpty}}"
            ,"                    {{#sqlCode}}"
            ,"                    <div class='box box-primary'>"
            ,"                        <div class='box-header with-border'>"
            ,"                            <i class='fa fa-file-code-o'></i>"
            ,"                            <h3 id='ViewDefinition' class='box-title'>View Definition</h3>"
            ,"                            <div class='box-tools pull-right'>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='collapse'><i class='fa fa-minus'></i></button>"
            ,"                                <button type='button' class='btn btn-box-tool' data-widget='remove'><i class='fa fa-times'></i></button>"
            ,"                            </div>"
            ,"                        </div>"
            ,"                        <div class='box-body'>"
            ,"                            <textarea id='sql-script-codemirror' name='sql-script-codemirror' rows='' style='display: none;'>{{sqlCode}}</textarea>"
            ,"                            <div style='padding-top: 5px;'><hr></div>"
            ,"                            <div class='box box-solid'>"
            ,"                                <div class='box-header with-border'>"
            ,"                                    <i class='fa fa-puzzle-piece'></i>"
            ,"                                    <h3 class='box-title'>Possibly Referenced Tables/Views</h3>"
            ,"                                </div>"
            ,"                                <!-- /.box-header -->"
            ,"                                <div class='box-body'>"
            ,"                                    <ul class='list-unstyled'>"
            ,"                                        {{#references}}"
            ,"                                        <li><a href='{{#toFileName}}{{name}}{{/toFileName}}.html'>{{name}}</a></li>"
            ,"                                        {{/references}}"
            ,"                                    </ul>"
            ,"                                </div><!-- /.box-body -->"
            ,"                            </div>"
            ,"                        </div><!-- /.box-body -->"
            ,"                    </div>"
            ,"                    {{/sqlCode}}"
            ,"                </section>"
            ,"                <script>"
            ,"                    var config = {"
            ,"                        pagination: {{paginationEnabled}}"
            ,"                    }"
            ,"                </script>"
            ));
        } catch (IOException e) {
            log.error(String.format(logFormat, "change template file error."), e);
            throw new GradleException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> findLayoutFile() {
        try {
            URL url = new URL("https://api.github.com/repos/schemaspy/schemaspy/git/trees/master?recursive=1");
            HttpURLConnection http = (HttpURLConnection) url.openConnection();
            Map<String, List<String>> header = http.getHeaderFields();
            while (isRedirected(header)) {
                URL redirectUrl = new URL(header.get("Location").get(0));
                http = (HttpURLConnection) redirectUrl.openConnection();
                header = http.getHeaderFields();
            }
            try (BufferedInputStream input = new BufferedInputStream(http.getInputStream());
                    InputStreamReader reader = new InputStreamReader(input);
                    BufferedReader output = new BufferedReader(reader)) {
                String text = output.lines().collect(Collectors.joining());
                Map<String, Object> jsonData = (Map<String, Object>) new JsonSlurper().parseText(text);
                List<Map<String, String>> treeData = ((List<Map<String, String>>) jsonData.get("tree"));
                return treeData.stream()
                        .filter(tree -> "blob".equals(tree.get("type")))
                        .map(tree -> tree.get("path"))
                        .filter(path -> path.startsWith("src/main/resources/layout/"))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GradleException(e.getMessage(), e); 
        }
    }

    private String getDriverJarPath(ERDbExtension databaseConnectExtension) {
        try {
            return databaseConnectExtension.getDriverFile().orElseGet(() -> {
                String databaseType = databaseConnectExtension.getDatabaseType();
                String databaseVersion = databaseConnectExtension.getDatabaseVersion().get();
                String jarFileName = null;
                String downloadJarUrl = null;
                try {
                    if ("mysql".equals(databaseType)) {
                        jarFileName = "mysql-connector-java-" + databaseVersion + ".jar";
                        downloadJarUrl = "https://repo1.maven.org/maven2/mysql/mysql-connector-java/" + databaseVersion + "/" + jarFileName;
                    } else if ("pgsql".equals(databaseType)) {
                        jarFileName = "postgresql-" + databaseVersion + ".jar";
                        downloadJarUrl = "https://repo1.maven.org/maven2/org/postgresql/postgresql/" + databaseVersion + "/" + jarFileName;
                    } else if ("mariadb".equals(databaseType)) {
                        jarFileName = "mariadb-java-client-" + databaseVersion + ".jar";
                        downloadJarUrl = "https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/" + databaseVersion + "/" + jarFileName;
                    } else {
                        log.error(String.format(logFormat, "please connecting driver file specify."));
                        throw new GradleException("driver download not supported.");
                    }
                    Path jarPath = schemaspyExecuteDir.resolve(jarFileName);
                    File jarFile = jarPath.toFile();
                    if (!jarFile.exists()) {
                        log.lifecycle(String.format(logFormat, "database driver jar not found."));
                        log.lifecycle(String.format(logFormat, "try download driver jar from " + downloadJarUrl));
                        Files.copy(new URL(downloadJarUrl).openStream(), jarPath, StandardCopyOption.REPLACE_EXISTING);
                        log.lifecycle(String.format(logFormat, "driver jar download completed: " + jarPath));
                    }
                    return jarFile;
                } catch (IOException e) {
                    log.error(String.format(logFormat, "jar file get error."), e);
                    throw new GradleException(e.getMessage(), e);
                }
            }).getCanonicalPath();
        } catch (IOException e) {
            log.error(String.format(logFormat, "driver jar setup error."), e);
            throw new GradleException(e.getMessage(), e);
        }
    }

    private String getExtensionRequiredSetting() {
        return String.join(System.lineSeparator(),
            "er task extension required connect setting.",
            "******************************************************",
            "yamlER {",
            "    er {",
            "      outDir = file('output directory')",
            "      schema = 'schema name'",
            "      db {",
            "        version = 'database (driver) version'",
            "        type = 'connect database type'",
            "        host = 'database host name'",
            "        port = 0000",
            "        database = 'database name'",
            "        user = 'accessible account user name'",
            "        password = 'accessible account user password'",
            "      }",
            "    }",
            "}",
            "******************************************************",
            "connect database type reference: https://github.com/schemaspy/schemaspy/tree/master/src/main/resources/org/schemaspy/types"
        );
    }


    @SuppressWarnings("unused")
    private void executeJarByFile(ERExtension extension, String schemaspyJarPath, List<String> applicationArgs) {
        String schemaName = extension.getSchema().orElse("dbtest");
        try {
            String dbUrl = Path.of(schemaspyJarPath).getParent().resolve("test").toString();
            applicationArgs.add("-t");
            applicationArgs.add("h2");
            applicationArgs.add("-s");
            applicationArgs.add(schemaName);

            // set connect user
            applicationArgs.add("-u");
            applicationArgs.add("sa");

            applicationArgs.add("-db");
            applicationArgs.add("file:" + dbUrl);

            String driverPath = org.h2.Driver.class.getProtectionDomain()
                                                   .getCodeSource()
                                                   .getLocation()
                                                   .toURI()
                                                   .getPath();
            applicationArgs.add("-loadjars");
            applicationArgs.add(driverPath);
            applicationArgs.add("-dp");
            applicationArgs.add(driverPath);
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:file:" + dbUrl + ";DATABASE_TO_LOWER=TRUE" + ";INIT\\=CREATE SCHEMA IF NOT EXISTS " + schemaName + "\\;SET SCHEMA " + schemaName + ";");
            dataSource.setUser("sa");
            try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
                statement.execute(Files.readString(extension.getDDLFile().toPath()));
            }
            setMain("-jar");
            setArgs(applicationArgs);
            super.exec();
        } catch (URISyntaxException | SQLException | IOException e) {

        }
    }


}