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

import java.util.Map;

import org.apache.tika.batch.ConsumersManager;
import org.apache.tika.batch.FileResourceCrawler;
import org.apache.tika.batch.StatusReporter;
import org.apache.tika.util.PropsUtil;
import org.apache.tika.util.XMLDOMUtil;
import org.w3c.dom.Node;

public class SimpleLogReporterBuilder implements StatusReporterBuilder {

    @Override
    public StatusReporter build(FileResourceCrawler crawler, ConsumersManager consumersManager,
                                Node n, Map<String, String> commandlineArguments) {

        Map<String, String> attributes = XMLDOMUtil.mapifyAttrs(n, commandlineArguments);
        long sleepMillis = PropsUtil.getLong(attributes.get("reporterSleepMillis"), 1000L);
        long staleThresholdMillis = PropsUtil.getLong(attributes.get("reporterStaleThresholdMillis"), 500000L);
        StatusReporter reporter = new StatusReporter(crawler, consumersManager);
        reporter.setSleepMillis(sleepMillis);
        reporter.setStaleThresholdMillis(staleThresholdMillis);
        return reporter;
    }
}
