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
package org.apache.tika.eval.batch;


import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.batch.ConsumersManager;
import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.batch.builders.AbstractConsumersBuilder;
import org.apache.tika.batch.builders.BatchProcessBuilder;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.eval.db.H2Util;
import org.apache.tika.eval.db.JDBCUtil;
import org.apache.tika.eval.db.MimeBuffer;
import org.apache.tika.eval.util.LanguageIDWrapper;
import org.apache.tika.util.ClassLoaderUtil;
import org.apache.tika.util.PropsUtil;
import org.apache.tika.util.XMLDOMUtil;
import org.w3c.dom.Node;

public class EvalConsumersBuilder extends AbstractConsumersBuilder {

    @Override
    public ConsumersManager build(Node node, Map<String, String> runtimeAttributes,
                                  ArrayBlockingQueue<FileResource> queue) {

        List<FileResourceConsumer> consumers = new LinkedList<>();
        int numConsumers = BatchProcessBuilder.getNumConsumers(runtimeAttributes);

        Map<String, String> localAttrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);


        Path db = getPath(localAttrs, "db");
        String jdbcConnectionString = localAttrs.get("jdbc");


        Path langModelDir = getPath(localAttrs, "langModelDir");

        try {
            if (langModelDir == null) {
                LanguageIDWrapper.loadBuiltInModels();
            } else {
                LanguageIDWrapper.loadModels(langModelDir);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path commonTokens = getPath(localAttrs, "commonTokens");
        String defaultLangCode = localAttrs.get("defaultLangCode");
        if (defaultLangCode == null || "".equals(defaultLangCode)) {
            defaultLangCode = "en";
        }
        //can be null, in which case will load from memory
        try {
            AbstractProfiler.loadCommonTokens(commonTokens, defaultLangCode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JDBCUtil jdbcUtil = null;
        if (db != null) {
            jdbcUtil = new H2Util(db);
        } else if (jdbcConnectionString != null) {
            jdbcUtil = new JDBCUtil(jdbcConnectionString, localAttrs.get("jdbcDriver"));
        } else {
            throw new RuntimeException("Must specify: -db or -jdbc");
        }
        EvalConsumerBuilder consumerBuilder = ClassLoaderUtil.buildClass(EvalConsumerBuilder.class,
                PropsUtil.getString(localAttrs.get("consumerBuilderClass"), null));
        if (consumerBuilder == null) {
            throw new RuntimeException("Must specify consumerBuilderClass in config file");
        }

        boolean forceDrop = PropsUtil.getBoolean(localAttrs.get("drop"), false);
        MimeBuffer mimeBuffer = null;
        try {
            mimeBuffer = consumerBuilder.init(queue, localAttrs, jdbcUtil, forceDrop);
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < numConsumers; i++) {
            try {
                consumers.add(consumerBuilder.build());
            } catch (IOException | SQLException e) {
                throw new RuntimeException(e);
            }
        }

        DBConsumersManager manager;
        try {
            manager = new DBConsumersManager(jdbcUtil, mimeBuffer, consumers);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        consumerBuilder.addErrorLogTablePairs(manager);

        return manager;
    }

    private Path getNonNullPath(Map<String, String> attrs, String key) {
        Path p = getPath(attrs, key);
        if (p == null) {
            throw new RuntimeException("Must specify a file for this attribute: "+key);
        }
        return p;
    }


    protected Path getPath(Map<String, String> attrs, String key) {
        String filePath = attrs.get(key);
        if (filePath == null) {
            return null;
        }
        return Paths.get(filePath);
    }


}
