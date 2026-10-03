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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/** The fork gets the parent's default locale unless forkedJvmArgs set one. */
// sets the JVM-wide default locale
@Isolated
public class PerClientServerManagerLocaleTest {

    @TempDir
    Path tmp;

    private List<String> userArgs(String... forkedJvmArgs) throws Exception {
        PipesConfig pipesConfig = new PipesConfig();
        pipesConfig.setForkedJvmArgs(new ArrayList<>(Arrays.asList(forkedJvmArgs)));
        PerClientServerManager manager =
                new PerClientServerManager(pipesConfig, tmp.resolve("tika-config.json"), 0);
        return Arrays.stream(manager.getCommandline(tmp))
                .filter(a -> a.startsWith("-Duser.")).toList();
    }

    @Test
    public void testParentLocalePropagates() throws Exception {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-nu-thai-x-lvariant-TH"));
            assertEquals(List.of("-Duser.language=th", "-Duser.country=TH", "-Duser.variant=TH",
                    "-Duser.extensions=u-nu-thai"), userArgs());
            Locale.setDefault(Locale.forLanguageTag("sr-Latn-RS"));
            assertEquals(List.of("-Duser.language=sr", "-Duser.script=Latn", "-Duser.country=RS"),
                    userArgs());
            Locale.setDefault(Locale.ROOT);
            assertEquals(List.of(), userArgs());
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testConfiguredLocaleWins() throws Exception {
        assertEquals(List.of("-Duser.language=tr", "-Duser.country=TR"),
                userArgs("-Duser.language=tr", "-Duser.country=TR"));
    }
}
