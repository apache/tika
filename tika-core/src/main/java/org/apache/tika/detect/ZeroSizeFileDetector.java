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
package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Detector to identify zero length files as application/x-zerovalue
 */
public class ZeroSizeFileDetector implements Detector {
    public MediaType detect(InputStream stream, Metadata metadata) throws IOException {
        if (stream != null) {
            try {
                stream.mark(1);
                if (stream.read() == -1) {
                    return MediaType.EMPTY;
                }
            } finally {
                stream.reset();
            }
        }
        return MediaType.OCTET_STREAM;
    }
}
