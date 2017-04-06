/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.eval;


import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Level;
import org.apache.tika.eval.db.Cols;
import org.apache.tika.eval.db.H2Util;
import org.apache.tika.eval.db.JDBCUtil;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.XMLLogMsgHandler;
import org.apache.tika.eval.io.XMLLogReader;
import org.apache.tika.eval.reports.ResultsReporter;
import org.apache.tika.io.IOExceptionWithCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a very task specific class that reads a log file and updates
 * the "comparisons" table.  It should not be run in a multithreaded environment.
 */
public class XMLErrorLogUpdater {
    private static final Logger LOG = LoggerFactory.getLogger(ResultsReporter.class);

    private Statement statement;

    public static void main(String[] args) throws Exception {
        XMLErrorLogUpdater writer = new XMLErrorLogUpdater();
        Path xmlLogFileA = Paths.get(args[0]);
        Path xmlLogFileB = Paths.get(args[1]);
        Path db = Paths.get(args[2]);
        JDBCUtil dbUtil = new H2Util(db);
        Connection connection = dbUtil.getConnection();
        writer.update(connection, ExtractComparer.EXTRACT_EXCEPTION_TABLE_A, xmlLogFileA);
        writer.update(connection, ExtractComparer.EXTRACT_EXCEPTION_TABLE_B, xmlLogFileB);
        connection.commit();
        connection.close();
    }

    public void update(Connection connection, TableInfo tableInfo, Path xmlLogFile) throws Exception {
        statement = connection.createStatement();
        XMLLogReader reader = new XMLLogReader();
        try (InputStream is = Files.newInputStream(xmlLogFile)) {
            reader.read(is, new ErrorMsgUpdater(tableInfo.getName()));
        } catch (IOException e) {
            throw new RuntimeException("Problem reading: "+xmlLogFile.toAbsolutePath().toString());
        } finally {
            try {
                connection.commit();
                statement.close();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to close db connection!", e);
            }
        }
    }

    private class ErrorMsgUpdater implements XMLLogMsgHandler {
        private final String errorTablename;

        private ErrorMsgUpdater(String errorTablename) {
            this.errorTablename = errorTablename;
        }

        @Override
        public void handleMsg(Level level, String xml) throws SQLException, IOException {
            if (! level.equals(Level.ERROR)) {
                return;
            }
            XMLStreamReader reader = null;
            try {
                reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
            } catch (XMLStreamException e) {
                throw new IOExceptionWithCause(e);
            }
            String type = null;
            String resourceId = null;
            try {
                while (reader.hasNext() && type == null && resourceId == null) {
                    reader.next();
                    switch (reader.getEventType()) {
                        case XMLStreamConstants.START_ELEMENT:
                            if ("timed_out".equals(reader.getLocalName())) {
                                resourceId = reader.getAttributeValue("", "resourceId");
                                update(errorTablename, resourceId,
                                        AbstractProfiler.PARSE_ERROR_TYPE.TIMEOUT);

                            } else if ("oom".equals(reader.getLocalName())) {
                                resourceId = reader.getAttributeValue("", "resourceId");
                                update(errorTablename, resourceId, AbstractProfiler.PARSE_ERROR_TYPE.OOM);
                            }
                            break;
                    }
                }
                reader.close();
            } catch (XMLStreamException e) {
                throw new IOExceptionWithCause(e);
            }
        }

        private void update(String errorTableName,
                            String filePath, AbstractProfiler.PARSE_ERROR_TYPE type) throws SQLException {
            int containerId = getContainerId(filePath);
            String sql = "SELECT count(1) from "+errorTableName +
                    " where "+Cols.CONTAINER_ID +
                    " = "+containerId + " or "+
                    Cols.FILE_PATH + "='"+filePath+"'";
            ResultSet rs = statement.executeQuery(sql);

            //now try to figure out if that file already exists
            //in parse errors
            int hitCount = 0;
            while (rs.next()) {
                hitCount = rs.getInt(1);
            }

            //if it does, update all records matching that path or container id
            if (hitCount > 0) {
                sql = "UPDATE " + errorTableName +
                        " SET " + Cols.PARSE_ERROR_ID +
                        " = " + type.ordinal() + ","+
                        Cols.FILE_PATH + "='" +filePath+"'"+
                        " where "+Cols.CONTAINER_ID +
                        "="+containerId + " or "+
                        Cols.FILE_PATH + "='"+filePath+"'";;

            } else {
                //if not and container id > -1
                //insert full record
                if (containerId > -1) {
                    sql = "INSERT INTO " + errorTableName +
                            " ("+Cols.CONTAINER_ID+","+Cols.FILE_PATH +","+Cols.PARSE_ERROR_ID +")"+
                            " values (" + containerId + ", '" + filePath + "'," +
                            type.ordinal() + ");";
                } else {
                    //if container id == -1, insert only file path and parse error type id
                    sql = "INSERT INTO " + errorTableName +
                            " ("+Cols.FILE_PATH.name()+","+Cols.PARSE_ERROR_ID +")"+
                            "values ('" + filePath + "'," +
                            type.ordinal() + ");";
                }

            }
            int updated = statement.executeUpdate(sql);
            if (updated == 0) {
                //TODO: log
                LOG.warn("made no updates in xmlerrorlogupdater!");
            } else if (updated > 1) {
                LOG.warn("made too many updates");
            }
        }

        private int getContainerId(String resourceId) throws SQLException {
            int containerId = -1;
            String sql = "SELECT " + Cols.CONTAINER_ID.name() +
                    " from " + ExtractProfiler.CONTAINER_TABLE.getName()+
                    " where " + Cols.FILE_PATH +
                    " ='"+resourceId+"'";
            ResultSet rs = statement.executeQuery(sql);
            int resultCount = 0;
            while (rs.next()) {
                containerId = rs.getInt(1);
                resultCount++;
            }
            rs.close();

            if (resultCount == 0) {
                LOG.warn("Should have found a container for: {}", resourceId);
            } else if (resultCount > 1) {
                LOG.error("Records ids should be unique: {}", resourceId);
            }
/*
            if (containerId < 0) {
                System.err.println("CONTAINER ID < 0!!!");
                sql = "SELECT MAX("+ Cols.CONTAINER_ID.name() +
                        ") from "+ExtractProfiler.CONTAINER_TABLE.getName();
                rs = statement.executeQuery(sql);
                while (rs.next()) {
                    containerId = rs.getInt(1);
                }
                rs.close();
                if (containerId < 0) {
                    //log and abort
                    //return -1?
                } else {
                    containerId++;
                }

            }*/
            return containerId;
        }


    }

}
