/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.language.translate.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Locale;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


/**
 * Test harness for the {@link RTGTranslator}.
 */
@Testcontainers(disabledWithoutDocker = true)
public class RTGTranslatorTest {

    static private RTGTranslator translator;
    static private GenericContainer<?> container;


    @BeforeAll
    static void setUp() {
        // ChatGPT, prompts used:
        // How can I run "docker run --rm -i -p 6060:6060 tgowda/rtg-model:500toEng-v1" with testcontainers in java?
        // What can I do if the container takes longer to initialize?
        DockerImageName imageName = DockerImageName.parse("tgowda/rtg-model:500toEng-v1");
        PortBinding portBinding = new PortBinding(Ports.Binding.bindPort(6060), new ExposedPort(6060));
        container = new GenericContainer<>(imageName)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(portBinding))
                .waitingFor(Wait.forHttp("/about")
                        .forPort(6060)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(5)));
        container.start();
        translator = new RTGTranslator();
    }

    @AfterAll
    static void finish() {
        container.close();
    }

    @Test
    public void testSimpleTranslate() {
        assumeTrue(translator.isAvailable());
        String source = "hola señor";
        String expected = "hello, sir.";

        try {
            String result = translator.translate(source);
            assertNotNull(result);
            assertEquals(expected, result.toLowerCase(Locale.getDefault()),
                    "Result: [" + result + "]: not equal to expected: [" + expected + "]");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
