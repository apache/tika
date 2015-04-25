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

package org.apache.tika.batch.fs.builders;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.batch.ConsumersManager;
import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.batch.OutputStreamFactory;
import org.apache.tika.batch.ParserFactory;
import org.apache.tika.batch.builders.AbstractConsumersBuilder;
import org.apache.tika.batch.builders.BatchProcessBuilder;
import org.apache.tika.batch.builders.IContentHandlerFactoryBuilder;
import org.apache.tika.batch.fs.BasicTikaFSConsumer;
import org.apache.tika.batch.fs.FSConsumersManager;
import org.apache.tika.batch.fs.FSOutputStreamFactory;
import org.apache.tika.batch.fs.FSUtil;
import org.apache.tika.batch.fs.RecursiveParserWrapperFSConsumer;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.util.ClassLoaderUtil;
import org.apache.tika.util.PropsUtil;
import org.apache.tika.util.XMLDOMUtil;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class BasicTikaFSConsumersBuilder extends AbstractConsumersBuilder {

    @Override
    public ConsumersManager build(Node node, Map<String, String> runtimeAttributes,
                                            ArrayBlockingQueue<FileResource> queue) {

        //figure out if we're building a recursiveParserWrapper
        boolean recursiveParserWrapper = false;
        String recursiveParserWrapperString = runtimeAttributes.get("recursiveParserWrapper");
        if (recursiveParserWrapperString != null){
            recursiveParserWrapper = PropsUtil.getBoolean(recursiveParserWrapperString, recursiveParserWrapper);
        } else {
            Node recursiveParserWrapperNode = node.getAttributes().getNamedItem("recursiveParserWrapper");
            if (recursiveParserWrapperNode != null) {
                recursiveParserWrapper = PropsUtil.getBoolean(recursiveParserWrapperNode.getNodeValue(), recursiveParserWrapper);
            }
        }

        //how long to let the consumersManager run on init() and shutdown()
        Long consumersManagerMaxMillis = null;
        String consumersManagerMaxMillisString = runtimeAttributes.get("consumersManagerMaxMillis");
        if (consumersManagerMaxMillisString != null){
            consumersManagerMaxMillis = PropsUtil.getLong(consumersManagerMaxMillisString, null);
        } else {
            Node consumersManagerMaxMillisNode = node.getAttributes().getNamedItem("consumersManagerMaxMillis");
            if (consumersManagerMaxMillis == null && consumersManagerMaxMillisNode != null) {
                consumersManagerMaxMillis = PropsUtil.getLong(consumersManagerMaxMillisNode.getNodeValue(),
                        null);
            }
        }

        TikaConfig config = null;
        String tikaConfigPath = runtimeAttributes.get("c");

        if( tikaConfigPath == null) {
            Node tikaConfigNode = node.getAttributes().getNamedItem("tikaConfig");
            if (tikaConfigNode != null) {
                tikaConfigPath = PropsUtil.getString(tikaConfigNode.getNodeValue(), null);
            }
        }
        if (tikaConfigPath != null) {
            try {
                config = new TikaConfig(new File(tikaConfigPath));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            config = TikaConfig.getDefaultConfig();
        }

        List<FileResourceConsumer> consumers = new LinkedList<FileResourceConsumer>();
        int numConsumers = BatchProcessBuilder.getNumConsumers(runtimeAttributes);

        NodeList nodeList = node.getChildNodes();
        Node contentHandlerFactoryNode = null;
        Node parserFactoryNode = null;
        Node outputStreamFactoryNode = null;

        for (int i = 0; i < nodeList.getLength(); i++){
            Node child = nodeList.item(i);
            String cn = child.getNodeName();
            if (cn.equals("parser")){
                parserFactoryNode = child;
            } else if (cn.equals("contenthandler")) {
                contentHandlerFactoryNode = child;
            } else if (cn.equals("outputstream")) {
                outputStreamFactoryNode = child;
            }
        }

        if (contentHandlerFactoryNode == null || parserFactoryNode == null
                || outputStreamFactoryNode == null) {
            throw new RuntimeException("You must specify a ContentHandlerFactory, "+
                    "a ParserFactory and an OutputStreamFactory");
        }
        ContentHandlerFactory contentHandlerFactory = getContentHandlerFactory(contentHandlerFactoryNode, runtimeAttributes);
        ParserFactory parserFactory = getParserFactory(parserFactoryNode, runtimeAttributes);
        OutputStreamFactory outputStreamFactory = getOutputStreamFactory(outputStreamFactoryNode, runtimeAttributes);

        if (recursiveParserWrapper) {
            for (int i = 0; i < numConsumers; i++) {
                FileResourceConsumer c = new RecursiveParserWrapperFSConsumer(queue,
                        parserFactory, contentHandlerFactory, outputStreamFactory, config);
                consumers.add(c);
            }
        } else {
            for (int i = 0; i < numConsumers; i++) {
                FileResourceConsumer c = new BasicTikaFSConsumer(queue,
                        parserFactory, contentHandlerFactory, outputStreamFactory, config);
                consumers.add(c);
            }
        }
        ConsumersManager manager = new FSConsumersManager(consumers);
        if (consumersManagerMaxMillis != null) {
            manager.setConsumersManagerMaxMillis(consumersManagerMaxMillis);
        }
        return manager;
    }


    private ContentHandlerFactory getContentHandlerFactory(Node node, Map<String, String> runtimeAttributes) {

        Map<String, String> localAttrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);
        String className = localAttrs.get("builderClass");
        if (className == null) {
            throw new RuntimeException("Must specify builderClass for contentHandler");
        }
        IContentHandlerFactoryBuilder builder = ClassLoaderUtil.buildClass(IContentHandlerFactoryBuilder.class, className);
        return builder.build(node, runtimeAttributes);
    }

    private ParserFactory getParserFactory(Node node, Map<String, String> runtimeAttributes) {
        //TODO: add ability to set TikaConfig file path
        Map<String, String> localAttrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);
        String className = localAttrs.get("class");
        return ClassLoaderUtil.buildClass(ParserFactory.class, className);
    }

    private OutputStreamFactory getOutputStreamFactory(Node node, Map<String, String> runtimeAttributes) {
        Map<String, String> attrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);

        File outputDir = PropsUtil.getFile(attrs.get("outputDir"), null);
/*        FSUtil.HANDLE_EXISTING handleExisting = null;
        String handleExistingString = attrs.get("handleExisting");
        if (handleExistingString == null) {
            handleExistingException();
        } else if (handleExistingString.equals("overwrite")){
            handleExisting = FSUtil.HANDLE_EXISTING.OVERWRITE;
        } else if (handleExistingString.equals("rename")) {
            handleExisting = FSUtil.HANDLE_EXISTING.RENAME;
        } else if (handleExistingString.equals("skip")) {
            handleExisting = FSUtil.HANDLE_EXISTING.SKIP;
        } else {
            handleExistingException();
        }
*/
        String compressionString = attrs.get("compression");
        FSOutputStreamFactory.COMPRESSION compression = FSOutputStreamFactory.COMPRESSION.NONE;
        if (compressionString == null) {
            //do nothing
        } else if (compressionString.contains("bz")) {
            compression = FSOutputStreamFactory.COMPRESSION.BZIP2;
        } else if (compressionString.contains("gz")) {
            compression = FSOutputStreamFactory.COMPRESSION.GZIP;
        } else if (compressionString.contains("zip")) {
            compression = FSOutputStreamFactory.COMPRESSION.ZIP;
        }
        String suffix = attrs.get("outputSuffix");

        //TODO: possibly open up the different handle existings in the future
        //but for now, lock it down to require skip.  Too dangerous otherwise
        //if the driver restarts and this is set to overwrite...
        return new FSOutputStreamFactory(outputDir, FSUtil.HANDLE_EXISTING.SKIP,
                compression, suffix);
    }

}
