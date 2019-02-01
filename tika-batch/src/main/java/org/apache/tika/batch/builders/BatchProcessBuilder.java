package org.apache.tika.batch.builders;

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

import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.batch.BatchProcess;
import org.apache.tika.batch.ConsumersManager;
import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceCrawler;
import org.apache.tika.batch.Interrupter;
import org.apache.tika.batch.StatusReporter;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.util.ClassLoaderUtil;
import org.apache.tika.util.XMLDOMUtil;
import org.apache.tika.utils.XMLReaderUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * Builds a BatchProcessor from a combination of runtime arguments and the
 * config file.
 */
public class BatchProcessBuilder {

    public final static int DEFAULT_MAX_QUEUE_SIZE = 1000;
    public final static String MAX_QUEUE_SIZE_KEY = "maxQueueSize";
    public final static String NUM_CONSUMERS_KEY = "numConsumers";

    /**
     * Builds a BatchProcess from runtime arguments and a
     * input stream of a configuration file.  With the exception of the QueueBuilder,
     * the builders choose how to adjudicate between
     * runtime arguments and the elements in the configuration file.
     * <p/>
     * This does not close the InputStream!
     * @param is inputStream
     * @param runtimeAttributes incoming runtime attributes
     * @return batch process
     * @throws java.io.IOException
     */
    public BatchProcess build(InputStream is, Map<String,String> runtimeAttributes) throws IOException {
        Document doc = null;
        try {
            DocumentBuilder docBuilder = XMLReaderUtils.getDocumentBuilder();
            doc = docBuilder.parse(is);
        } catch (TikaException|SAXException e) {
            throw new IOExceptionWithCause(e);
        }
        Node docElement = doc.getDocumentElement();
        return build(docElement, runtimeAttributes);
    }

    /**
     * Builds a FileResourceBatchProcessor from runtime arguments and a
     * document node of a configuration file.  With the exception of the QueueBuilder,
     * the builders choose how to adjudicate between
     * runtime arguments and the elements in the configuration file.
     *
     * @param docElement   document element of the xml config file
     * @param incomingRuntimeAttributes runtime arguments
     * @return FileResourceBatchProcessor
     */
    public BatchProcess build(Node docElement, Map<String, String> incomingRuntimeAttributes) {

        //key components
        long timeoutThresholdMillis = XMLDOMUtil.getLong("timeoutThresholdMillis",
                incomingRuntimeAttributes, docElement);
        long timeoutCheckPulseMillis = XMLDOMUtil.getLong("timeoutCheckPulseMillis",
                incomingRuntimeAttributes, docElement);
        long pauseOnEarlyTerminationMillis = XMLDOMUtil.getLong("pauseOnEarlyTerminationMillis",
                incomingRuntimeAttributes, docElement);
        int maxAliveTimeSeconds = XMLDOMUtil.getInt("maxAliveTimeSeconds",
                incomingRuntimeAttributes, docElement);

        FileResourceCrawler crawler = null;
        ConsumersManager consumersManager = null;
        StatusReporter reporter = null;
        Interrupter interrupter = null;

        /*
         * TODO: This is a bit smelly.  NumConsumers needs to be used by the crawler
         * and the consumers.  This copies the incomingRuntimeAttributes and then
         * supplies the numConsumers from the commandline (if it exists) or from the config file
         * At least this creates an unmodifiable defensive copy of incomingRuntimeAttributes...
         */
        Map<String, String> runtimeAttributes = setNumConsumersInRuntimeAttributes(docElement, incomingRuntimeAttributes);

        //build queue
        ArrayBlockingQueue<FileResource> queue = buildQueue(docElement, runtimeAttributes);

        NodeList children = docElement.getChildNodes();
        Map<String, Node> keyNodes = new HashMap<String, Node>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String nodeName = child.getNodeName();
            keyNodes.put(nodeName, child);
        }
        //build consumers
        consumersManager = buildConsumersManager(keyNodes.get("consumers"), runtimeAttributes, queue);

        //build crawler
        crawler = buildCrawler(queue, keyNodes.get("crawler"), runtimeAttributes);

        if (keyNodes.containsKey(reporter)) {
            reporter = buildReporter(crawler, consumersManager, keyNodes.get("reporter"), runtimeAttributes);
        }

        if (keyNodes.containsKey("interrupter")) {
            interrupter = buildInterrupter(keyNodes.get("interrupter"), pauseOnEarlyTerminationMillis, runtimeAttributes);
        }
        BatchProcess proc = new BatchProcess(
                crawler, consumersManager, reporter, interrupter);

        if (timeoutThresholdMillis > -1) {
            proc.setTimeoutThresholdMillis(timeoutThresholdMillis);
        }

        if (pauseOnEarlyTerminationMillis > -1) {
            proc.setPauseOnEarlyTerminationMillis(pauseOnEarlyTerminationMillis);
        }

        if (timeoutCheckPulseMillis > -1) {
            proc.setTimeoutCheckPulseMillis(timeoutCheckPulseMillis);
        }
        proc.setMaxAliveTimeSeconds(maxAliveTimeSeconds);
        return proc;
    }

    private Interrupter buildInterrupter(Node node, long pauseOnEarlyTermination, Map<String, String> runtimeAttributes) {
        Map<String, String> attrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);
        String className = attrs.get("builderClass");
        if (className == null) {
            throw new RuntimeException("Need to specify class name in interrupter element");
        }
        InterrupterBuilder builder = ClassLoaderUtil.buildClass(InterrupterBuilder.class, className);

        return builder.build(node, pauseOnEarlyTermination, runtimeAttributes);

    }

    private StatusReporter buildReporter(FileResourceCrawler crawler, ConsumersManager consumersManager,
                                          Node node, Map<String, String> runtimeAttributes) {

        Map<String, String> attrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);
        String className = attrs.get("builderClass");
        if (className == null) {
            throw new RuntimeException("Need to specify class name in reporter element");
        }
        StatusReporterBuilder builder = ClassLoaderUtil.buildClass(StatusReporterBuilder.class, className);

        return builder.build(crawler, consumersManager, node, runtimeAttributes);

    }

    /**
     * numConsumers is needed by both the crawler and the consumers. This utility method
     * is to be used to extract the number of consumers from a map of String key value pairs.
     * <p>
     * If the value is "default", not a parseable integer or has a value &lt; 1,
     * then <code>AbstractConsumersBuilder</code>'s <code>getDefaultNumConsumers()</code>
     * @param attrs attributes from which to select the NUM_CONSUMERS_KEY
     * @return number of consumers
     */
    public static int getNumConsumers(Map<String, String> attrs) {
        String nString = attrs.get(BatchProcessBuilder.NUM_CONSUMERS_KEY);
        if (nString == null || nString.equals("default")) {
            return AbstractConsumersBuilder.getDefaultNumConsumers();
        }
        int n = -1;
        try {
            n = Integer.parseInt(nString);
        } catch (NumberFormatException e) {
            //swallow
        }
        if (n < 1) {
            n = AbstractConsumersBuilder.getDefaultNumConsumers();
        }
        return n;
    }

    private Map<String, String> setNumConsumersInRuntimeAttributes(Node docElement, Map<String, String> incomingRuntimeAttributes) {
        Map<String, String> runtimeAttributes = new HashMap<String, String>();

        for(Map.Entry<String, String> e : incomingRuntimeAttributes.entrySet()) {
            runtimeAttributes.put(e.getKey(), e.getValue());
        }

        //if this is set at runtime use that value
        if (runtimeAttributes.containsKey(NUM_CONSUMERS_KEY)){
            return Collections.unmodifiableMap(runtimeAttributes);
        }
        Node ncNode = docElement.getAttributes().getNamedItem("numConsumers");
        int numConsumers = -1;
        String numConsumersString = ncNode.getNodeValue();
        try {
            numConsumers = Integer.parseInt(numConsumersString);
        } catch (NumberFormatException e) {
            //swallow and just use numConsumers
        }
        //TODO: should we have a max range check?
        if (numConsumers < 1) {
            numConsumers = AbstractConsumersBuilder.getDefaultNumConsumers();
        }
        runtimeAttributes.put(NUM_CONSUMERS_KEY, Integer.toString(numConsumers));
        return Collections.unmodifiableMap(runtimeAttributes);
    }

    //tries to get maxQueueSize from main element
    private ArrayBlockingQueue<FileResource> buildQueue(Node docElement,
                                                        Map<String, String> runtimeAttributes) {
        int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
        String szString = runtimeAttributes.get(MAX_QUEUE_SIZE_KEY);

        if (szString == null) {
            Node szNode = docElement.getAttributes().getNamedItem(MAX_QUEUE_SIZE_KEY);
            if (szNode != null) {
                szString = szNode.getNodeValue();
            }
        }

        if (szString != null) {
            try {
                maxQueueSize = Integer.parseInt(szString);
            } catch (NumberFormatException e) {
                //swallow
            }
        }

        if (maxQueueSize < 0) {
            maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
        }

        return new ArrayBlockingQueue<FileResource>(maxQueueSize);
    }

    private ConsumersManager buildConsumersManager(Node node,
                Map<String, String> runtimeAttributes, ArrayBlockingQueue<FileResource> queue) {

        Map<String, String> attrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);
        String className = attrs.get("builderClass");
        if (className == null) {
            throw new RuntimeException("Need to specify class name in consumers element");
        }
        AbstractConsumersBuilder builder = ClassLoaderUtil.buildClass(AbstractConsumersBuilder.class, className);

        return builder.build(node, runtimeAttributes, queue);
    }


    private FileResourceCrawler buildCrawler(ArrayBlockingQueue<FileResource> queue,
                                             Node node, Map<String, String> runtimeAttributes) {
        Map<String, String> attrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);
        String className = attrs.get("builderClass");
        if (className == null) {
            throw new RuntimeException("Need to specify class name in crawler element");
        }

        ICrawlerBuilder builder = ClassLoaderUtil.buildClass(ICrawlerBuilder.class, className);
        return builder.build(node, runtimeAttributes, queue);
    }





}
