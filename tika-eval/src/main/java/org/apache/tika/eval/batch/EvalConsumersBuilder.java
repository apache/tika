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
import org.apache.tika.config.TikaConfig;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.eval.db.DBUtil;
import org.apache.tika.eval.db.H2Util;
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
        try {
            AbstractProfiler.loadCommonTokens(commonTokens);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        boolean append = PropsUtil.getBoolean(localAttrs.get("dbAppend"), false);

        if (db == null) {
            throw new RuntimeException("Must specify: -db");
        }
        //parameterize which db util to use
        DBUtil util = new H2Util(db);
        EvalConsumerBuilder consumerBuilder = ClassLoaderUtil.buildClass(EvalConsumerBuilder.class,
                PropsUtil.getString(localAttrs.get("consumerBuilderClass"), null));
        if (consumerBuilder == null) {
            throw new RuntimeException("Must specify consumerBuilderClass in config file");
        }


        MimeBuffer mimeBuffer = null;
        try {
            util.createDB(consumerBuilder.getTableInfo(), append);
            mimeBuffer = new MimeBuffer(util.getConnection(true), TikaConfig.getDefaultConfig());
            consumerBuilder.init(queue, localAttrs, util, mimeBuffer);
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
            manager = new DBConsumersManager(util, mimeBuffer, consumers);
        } catch (IOException e) {
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
