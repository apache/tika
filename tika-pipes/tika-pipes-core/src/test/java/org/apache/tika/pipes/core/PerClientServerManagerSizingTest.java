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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Auto-injected fork sizing (-XX:MaxRAMPercentage, -XX:ActiveProcessorCount)
 * applies uniformly, including numClients=1, unless the user set their own.
 */
public class PerClientServerManagerSizingTest {

    @TempDir
    Path tmp;

    private List<String> commandLine(int numClients, String... forkedJvmArgs) throws Exception {
        PipesConfig pipesConfig = new PipesConfig();
        pipesConfig.setNumClients(numClients);
        pipesConfig.setForkedJvmArgs(new ArrayList<>(Arrays.asList(forkedJvmArgs)));
        PerClientServerManager manager =
                new PerClientServerManager(pipesConfig, tmp.resolve("tika-config.json"), 0);
        return Arrays.asList(manager.getCommandline(tmp));
    }

    private static List<String> withPrefix(List<String> args, String prefix) {
        return args.stream().filter(a -> a.startsWith(prefix)).toList();
    }

    /** A lone fork is capped below the fork budget; the parent claims the JVM default on top. */
    @Test
    public void heapInjectedForSingleClient() throws Exception {
        assertEquals(List.of("-XX:MaxRAMPercentage=60"),
                withPrefix(commandLine(1), "-XX:MaxRAMPercentage"));
    }

    @Test
    public void heapDividedAcrossClients() throws Exception {
        assertEquals(List.of("-XX:MaxRAMPercentage=25"),
                withPrefix(commandLine(3), "-XX:MaxRAMPercentage"));
    }

    @Test
    public void userHeapSuppressesInjection() throws Exception {
        List<String> args = commandLine(1, "-Xmx512m");
        assertTrue(withPrefix(args, "-XX:MaxRAMPercentage").isEmpty(),
                "user -Xmx must suppress auto MaxRAMPercentage: " + args);
        assertTrue(args.contains("-Xmx512m"));
    }

    @Test
    public void cpuCapInjectedForSingleClient() throws Exception {
        List<String> caps = withPrefix(commandLine(1), "-XX:ActiveProcessorCount=");
        int slice = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        if (slice >= 2) {
            assertEquals(List.of("-XX:ActiveProcessorCount=" + slice), caps);
        } else {
            // Host too small for the auto-cap; injection is skipped by design.
            assertTrue(caps.isEmpty(), "expected no auto-cap on tiny host: " + caps);
        }
    }

    @Test
    public void userCpuCapSuppressesInjection() throws Exception {
        assertEquals(List.of("-XX:ActiveProcessorCount=3"),
                withPrefix(commandLine(1, "-XX:ActiveProcessorCount=3"),
                        "-XX:ActiveProcessorCount="));
    }

    @Test
    public void parseJvmMemArg() {
        assertEquals(512L * 1024 * 1024, PerClientServerManager.parseJvmMemArg("512m"));
        assertEquals(2L * 1024 * 1024 * 1024, PerClientServerManager.parseJvmMemArg("2g"));
        assertEquals(1024L * 1024, PerClientServerManager.parseJvmMemArg("1024k"));
        assertEquals(1L * 1024 * 1024 * 1024 * 1024, PerClientServerManager.parseJvmMemArg("1t"));
        assertEquals(1000, PerClientServerManager.parseJvmMemArg("1000"));
        assertEquals(-1, PerClientServerManager.parseJvmMemArg("abc"));
        assertEquals(-1, PerClientServerManager.parseJvmMemArg(""));
        assertEquals(-1, PerClientServerManager.parseJvmMemArg("-2g"));
        assertEquals(-1, PerClientServerManager.parseJvmMemArg("999999999t"));
    }

    private static final long GB = 1024L * 1024 * 1024;

    @Test
    public void xmxOvercommitWarns() {
        // 4 x 2g = 8g > 75% of 8g
        assertNotNull(PerClientServerManager.heapOvercommitWarning(
                List.of("-Xmx2g"), 4, 8 * GB));
        // 4 x 2g = 8g <= 75% of 16g
        assertNull(PerClientServerManager.heapOvercommitWarning(
                List.of("-Xmx2g"), 4, 16 * GB));
        // last -Xmx wins, same as the JVM
        assertNull(PerClientServerManager.heapOvercommitWarning(
                List.of("-Xmx8g", "-Xmx1g"), 4, 16 * GB));
        // unknown total memory: no basis to warn
        assertNull(PerClientServerManager.heapOvercommitWarning(
                List.of("-Xmx2g"), 4, -1));
    }

    @Test
    public void maxRamPercentageOvercommitWarns() {
        // 3 x 50% = 150% of memory
        assertNotNull(PerClientServerManager.heapOvercommitWarning(
                List.of("-XX:MaxRAMPercentage=50"), 3, -1));
        // 3 x 25% = 75% budget exactly: allowed
        assertNull(PerClientServerManager.heapOvercommitWarning(
                List.of("-XX:MaxRAMPercentage=25"), 3, -1));
        // a fitting -Xmx overrides an overcommitted percentage, same as the JVM
        assertNull(PerClientServerManager.heapOvercommitWarning(
                List.of("-XX:MaxRAMPercentage=50", "-Xmx1g"), 3, 16 * GB));
    }

    @Test
    public void noExplicitHeapNoWarning() {
        assertNull(PerClientServerManager.heapOvercommitWarning(List.of(), 8, 4 * GB));
    }
}
