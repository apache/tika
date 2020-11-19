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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.Set;

public class ByteInjector implements Transformer {
    Random random = new Random();
    float injectionFrequency = 0.01f;
    int maxSpan = 100;
    static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.OCTET_STREAM);

    @Override
    public Set<MediaType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public void transform(InputStream is, OutputStream os) throws IOException {
        //TODO -- don't load the full thing into memory
        byte[] input = IOUtils.toByteArray(is);
        int numInjections = (int) Math.floor((double)injectionFrequency*(double)input.length);
        //at least one injection
        numInjections = numInjections == 0 ? 1 : numInjections;
        int[] starts = new int[numInjections];
        if (numInjections > 1) {
            for (int i = 0; i < numInjections; i++) {
                starts[i] = random.nextInt(input.length - 1);
            }
        } else {
            starts[0] = 0;
        }
        Arrays.sort(starts);
        int startIndex = 0;

        for (int i = 0; i < input.length; i++) {
            os.write(input[i]);
            if (startIndex < starts.length && starts[startIndex] == i) {
                inject(os);
                startIndex++;
            }
        }
    }

    private void inject(OutputStream os) throws IOException {
        int len = random.nextInt(maxSpan);
        byte[] randBytes = new byte[len];
        random.nextBytes(randBytes);
        os.write(randBytes);
    }
}
