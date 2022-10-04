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
package org.apache.tika.pipes.solr.tests;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.apache.tika.utils.SystemUtils;

@Testcontainers(disabledWithoutDocker = true)
public class TikaPipesSolr6ZkTest extends TikaPipesSolr6Test {

    @BeforeAll
    public static void setUp() {
        assumeTrue(
                SystemUtils.IS_OS_UNIX && !SystemUtils.IS_OS_MAC_OSX,
                "zk test only works on linux (and not mac os x)");
    }

    @Override
    public boolean useZk() {
        return true;
    }

}
