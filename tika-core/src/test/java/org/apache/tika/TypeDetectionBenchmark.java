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
package org.apache.tika;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.tika.io.IOUtils;

public class TypeDetectionBenchmark {

    private static final Tika tika = new Tika();

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        if (args.length > 0) {
            for (String arg : args) {
                benchmark(new File(arg));
            }
        } else {
            benchmark(new File(
                    "../tika-parsers/src/test/resources/test-documents"));
        }
        System.out.println(
                "Total benchmark time: "
                + (System.currentTimeMillis() - start) + "ms");
    }

    private static void benchmark(File file) throws Exception {
        if (file.isHidden()) {
            // ignore
        } else if (file.isFile()) {
            InputStream input = new FileInputStream(file);
            try {
                byte[] content = IOUtils.toByteArray(input);
                String type =
                    tika.detect(new ByteArrayInputStream(content));
                long start = System.currentTimeMillis();
                for (int i = 0; i < 1000; i++) {
                    tika.detect(new ByteArrayInputStream(content));
                }
                System.out.printf(
                        "%6dns per Tika.detect(%s) = %s%n",
                        System.currentTimeMillis() - start, file, type);
            } finally {
                input.close();
            }
        } else if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                benchmark(child);
            }
        }
    }

}
