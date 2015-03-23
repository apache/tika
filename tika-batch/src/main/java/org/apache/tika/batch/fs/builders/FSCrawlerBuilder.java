package org.apache.tika.batch.fs.builders;

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


import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceCrawler;
import org.apache.tika.batch.builders.BatchProcessBuilder;
import org.apache.tika.batch.builders.ICrawlerBuilder;
import org.apache.tika.batch.fs.FSDirectoryCrawler;
import org.apache.tika.batch.fs.FSDocumentSelector;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.util.PropsUtil;
import org.apache.tika.util.XMLDOMUtil;
import org.w3c.dom.Node;

/**
 * Builds either an FSDirectoryCrawler or an FSListCrawler.
 */
public class FSCrawlerBuilder implements ICrawlerBuilder {

    private final static String MAX_CONSEC_WAIT_MILLIS = "maxConsecWaitMillis";
    private final static String MAX_FILES_TO_ADD_ATTR = "maxFilesToAdd";
    private final static String MAX_FILES_TO_CONSIDER_ATTR = "maxFilesToConsider";


    private final static String CRAWL_ORDER = "crawlOrder";
    private final static String INPUT_DIR_ATTR = "inputDir";
    private final static String INPUT_START_DIR_ATTR = "startDir";
    private final static String MAX_FILE_SIZE_BYTES_ATTR = "maxFileSizeBytes";
    private final static String MIN_FILE_SIZE_BYTES_ATTR = "minFileSizeBytes";


    private final static String INCLUDE_FILE_PAT_ATTR = "includeFilePat";
    private final static String EXCLUDE_FILE_PAT_ATTR = "excludeFilePat";

    @Override
    public FileResourceCrawler build(Node node, Map<String, String> runtimeAttributes,
                                     ArrayBlockingQueue<FileResource> queue) {

        Map<String, String> attributes = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);

        int numConsumers = BatchProcessBuilder.getNumConsumers(runtimeAttributes);
        File inputDir = PropsUtil.getFile(attributes.get(INPUT_DIR_ATTR), new File("input"));
        FileResourceCrawler crawler = null;
        if (attributes.containsKey("fileList")) {
            String randomCrawlString = attributes.get(CRAWL_ORDER);

            if (randomCrawlString != null) {
                //TODO: change to logger warn or throw RuntimeException?
                System.err.println("randomCrawl attribute is ignored by FSListCrawler");
            }
            File fileList = PropsUtil.getFile(attributes.get("fileList"), null);
            String encoding = PropsUtil.getString(attributes.get("fileListEncoding"), "UTF-8");
            try {
                crawler = new org.apache.tika.batch.fs.FSListCrawler(queue, numConsumers, inputDir, fileList, encoding);
            } catch (java.io.FileNotFoundException e) {
                throw new RuntimeException("fileList file not found for FSListCrawler: " + fileList.getAbsolutePath());
            } catch (java.io.UnsupportedEncodingException e) {
                throw new RuntimeException("fileList encoding not supported: "+encoding);
            }
        } else {
            FSDirectoryCrawler.CRAWL_ORDER crawlOrder = getCrawlOrder(attributes.get(CRAWL_ORDER));
            File startDir = PropsUtil.getFile(attributes.get(INPUT_START_DIR_ATTR), null);
            if (startDir == null) {
                crawler = new FSDirectoryCrawler(queue, numConsumers, inputDir, crawlOrder);
            } else {
                crawler = new FSDirectoryCrawler(queue, numConsumers, inputDir, startDir, crawlOrder);
            }
        }

        crawler.setMaxFilesToConsider(PropsUtil.getInt(attributes.get(MAX_FILES_TO_CONSIDER_ATTR), -1));
        crawler.setMaxFilesToAdd(PropsUtil.getInt(attributes.get(MAX_FILES_TO_ADD_ATTR), -1));

        DocumentSelector selector = buildSelector(attributes);
        if (selector != null) {
            crawler.setDocumentSelector(selector);
        }

        crawler.setMaxConsecWaitInMillis(PropsUtil.getLong(attributes.get(MAX_CONSEC_WAIT_MILLIS), 300000L));//5 minutes
        return crawler;
    }

    private FSDirectoryCrawler.CRAWL_ORDER getCrawlOrder(String s) {
        if (s == null || s.trim().length() == 0 || s.equals("os")) {
            return FSDirectoryCrawler.CRAWL_ORDER.OS_ORDER;
        } else if (s.toLowerCase(Locale.ROOT).contains("rand")) {
            return FSDirectoryCrawler.CRAWL_ORDER.RANDOM;
        } else if (s.toLowerCase(Locale.ROOT).contains("sort")) {
            return FSDirectoryCrawler.CRAWL_ORDER.SORTED;
        } else {
            return FSDirectoryCrawler.CRAWL_ORDER.OS_ORDER;
        }
    }

    private DocumentSelector buildSelector(Map<String, String> attributes) {
        String includeString = attributes.get(INCLUDE_FILE_PAT_ATTR);
        String excludeString = attributes.get(EXCLUDE_FILE_PAT_ATTR);
        long maxFileSize = PropsUtil.getLong(attributes.get(MAX_FILE_SIZE_BYTES_ATTR), -1L);
        long minFileSize = PropsUtil.getLong(attributes.get(MIN_FILE_SIZE_BYTES_ATTR), -1L);
        Pattern includePat = (includeString != null && includeString.length() > 0) ? Pattern.compile(includeString) : null;
        Pattern excludePat = (excludeString != null && excludeString.length() > 0) ? Pattern.compile(excludeString) : null;

        return new FSDocumentSelector(includePat, excludePat, minFileSize, maxFileSize);
    }


}
