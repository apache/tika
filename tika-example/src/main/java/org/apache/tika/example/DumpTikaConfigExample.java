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

package org.apache.tika.example;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.config.TikaConfigSerializer;


/**
 * This class shows how to dump a TikaConfig object to a configuration file.
 * This allows users to easily dump the default TikaConfig as a base from which
 * to start if they want to modify the default configuration file.
 * <p>
 * For those who want to modify the mimes file, take a look at
 * tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml
 * for inspiration.  Consider adding org/apache/tika/mime/custom-mimetypes.xml
 * for your custom mime types.
 */
public class DumpTikaConfigExample {

    /**
     * @param args outputFile, outputEncoding, if args is empty, this prints to console
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        Charset encoding = UTF_8;
        TikaConfigSerializer.Mode mode = TikaConfigSerializer.Mode.CURRENT;
        String filename = null;

        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.contains("-dump-minimal")) {
                    mode = TikaConfigSerializer.Mode.MINIMAL;
                } else if (arg.contains("-dump-current")) {
                    mode = TikaConfigSerializer.Mode.CURRENT;
                } else if (arg.contains("-dump-static")) {
                    mode = TikaConfigSerializer.Mode.STATIC;
                } else {
                    System.out.println("Use:");
                    System.out.println(
                            "  DumpTikaConfig [--dump-minimal] [--dump-current] [--dump-static] [filename] [encoding]");
                    System.out.println("");
                    System.out.println("--dump-minimal    Produce the minimal config file");
                    System.out.println("--dump-current    The current (with defaults) config file");
                    System.out.println("--dump-static     Convert dynamic parts to static");
                    return;
                }
            } else if (filename == null) {
                filename = arg;
            } else {
                encoding = Charset.forName(arg);
            }
        }

        Writer writer = null;
        if (filename != null) {
            writer = new OutputStreamWriter(new FileOutputStream(filename), encoding);
        } else {
            writer = new StringWriter();
        }

        DumpTikaConfigExample ex = new DumpTikaConfigExample();
        TikaConfigSerializer.serialize(TikaConfig.getDefaultConfig(), mode, writer, encoding);

        writer.flush();

        if (writer instanceof StringWriter) {
            System.out.println(writer.toString());
        }
        writer.close();
    }

}
