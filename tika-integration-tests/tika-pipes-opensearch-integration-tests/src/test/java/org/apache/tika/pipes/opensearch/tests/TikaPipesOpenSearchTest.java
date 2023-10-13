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
package org.apache.tika.pipes.opensearch.tests;


import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.apache.tika.pipes.xsearch.tests.TikaPipesXSearchBase;

@Disabled("until we can figure out why this is failing on github actions (TIKA-4155_")
@Testcontainers(disabledWithoutDocker = true)
public class TikaPipesOpenSearchTest extends TikaPipesXSearchBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaPipesOpenSearchTest.class);
    private static final String DOCKER_IMAGE_NAME = "opensearchproject/opensearch:2.10.0";

    @Container
    public static GenericContainer<?> OPEN_SEARCH_CONTAINER =
            new GenericContainer<>(DockerImageName.parse(DOCKER_IMAGE_NAME))
                    .withExposedPorts(9200)
                    .withStartupTimeout(Duration.of(180, ChronoUnit.SECONDS))
                    .withEnv("discovery.type", "single-node");


    @BeforeEach
    public void setupTest() throws Exception {
        setupXSearch(OPEN_SEARCH_CONTAINER, "https://");
        LOG.info("opensearch container: {}", OPEN_SEARCH_CONTAINER);
    }
}
