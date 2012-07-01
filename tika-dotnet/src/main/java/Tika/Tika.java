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
package Tika;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.tika.exception.TikaException;

public class Tika {

    private final org.apache.tika.Tika tika = new org.apache.tika.Tika();

    public cli.System.String detect(cli.System.String name) {
        return toCliString(tika.detect(toJvmString(name)));
    }

    public cli.System.String detect(cli.System.IO.FileInfo file)
            throws cli.System.IO.IOException {
        try {
            return toCliString(tika.detect(new File(file.get_FullName())));
        } catch (IOException e) {
            throw new cli.System.IO.IOException(e.getMessage(), e);
        }
    }

    public cli.System.String detect(cli.System.Uri uri)
            throws cli.System.IO.IOException {
        try {
            return toCliString(tika.detect(new URL(uri.get_AbsolutePath())));
        } catch (IOException e) {
            throw new cli.System.IO.IOException(e.getMessage(), e);
        }
    }

    public cli.System.String parseToString(cli.System.IO.FileInfo file)
            throws cli.System.IO.IOException, TikaException {
        try {
            return toCliString(tika.parseToString(new File(file.get_FullName())));
        } catch (IOException e) {
            throw new cli.System.IO.IOException(e.getMessage(), e);
        }
    }

    public cli.System.String parseToString(cli.System.Uri uri)
            throws cli.System.IO.IOException, TikaException {
        try {
            return toCliString(tika.parseToString(new URL(uri.get_AbsoluteUri())));
        } catch (IOException e) {
            throw new cli.System.IO.IOException(e.getMessage(), e);
        }
    }

    private static cli.System.String toCliString(String string) {
        return new cli.System.String(string.toCharArray());
    }

    private static String toJvmString(cli.System.String string) {
        return new String(string.ToCharArray());
    }

}
