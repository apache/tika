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
package org.apache.tika.eval.app.tools;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

public class TrainTestSplit {

    private static String TRAINING = "train";
    private static String TESTING = "test";
    private static String DEVTEST = "devtest";

    private float trainingP = 0.7f;
    private float devTestP = 0.1f;
    //private float testP = 0.2f;
    private Random random = new Random();

    public static void main(String[] args) throws Exception {
        Path leipzigDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        TrainTestSplit splitter = new TrainTestSplit();
        splitter.execute(leipzigDir, outputDir);
    }

    private void execute(Path leipzigDir, Path outputDir) throws Exception {
        initOutDirs(outputDir);
        for (File f : leipzigDir.toFile().listFiles()) {
            if (f.isDirectory()) {
                continue;
            }
            processFile(f, outputDir);
        }
    }

    private void initOutDirs(Path outputDir) throws Exception {
        for (String which : new String[]{TRAINING, DEVTEST, TESTING}) {
            Path target = outputDir.resolve(which);
            if (!Files.isDirectory(target)) {
                Files.createDirectories(target);
            }
        }

    }

    private void processFile(File f, Path outputDir) throws Exception {
        Map<String, BufferedWriter> writers = getWriters(outputDir, f);
        System.err.println("working on " + f);
        try (BufferedReader reader = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while (line != null) {
                float r = random.nextFloat();
                if (r <= trainingP) {
                    writers.get(TRAINING).write(line + "\n");
                } else if (r < trainingP + devTestP) {
                    writers.get(DEVTEST).write(line + "\n");
                } else {
                    writers.get(TESTING).write(line + "\n");
                }
                line = reader.readLine();
            }
        }


        for (Writer w : writers.values()) {
            w.flush();
            w.close();
        }
    }

    private Map<String, BufferedWriter> getWriters(Path outputDir, File f) throws IOException {
        Map<String, BufferedWriter> writers = new HashMap<>();
        for (String which : new String[]{TRAINING, DEVTEST, TESTING}) {
            writers.put(which, getWriter(outputDir, which, f));
        }
        return writers;
    }

    private BufferedWriter getWriter(Path outputDir, String which, File f) throws IOException {
        OutputStream os = new GzipCompressorOutputStream(new BufferedOutputStream(
                Files.newOutputStream(outputDir.resolve(which).resolve(f.getName() + ".gz"))));
        return new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
    }
}
