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
package org.apache.tika.fuzzing.general;

import org.apache.commons.io.IOUtils;
import org.apache.tika.fuzzing.Transformer;
import org.apache.tika.mime.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Random;
import java.util.Set;

public class Truncator implements Transformer {

    Random random = new Random();
    static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.OCTET_STREAM);

    @Override
    public Set<MediaType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public void transform(InputStream is, OutputStream os) throws IOException {
        //TODO -- redo streaming
        byte[] input = IOUtils.toByteArray(is);
        if (input.length == 0) {
            return;
        }
        int len = 1 + random.nextInt(input.length);
        //at least one
        if (len >= input.length) {
            len = input.length-2;
            if (len < 0) {
                len = 0;
            }
        }

        byte[] ret = new byte[len];
        System.arraycopy(input, 0, ret, 0, len);
        os.write(ret);
    }
}
