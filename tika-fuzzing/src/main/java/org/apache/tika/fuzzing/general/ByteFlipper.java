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

public class ByteFlipper implements Transformer {

    //TODO add something about protecting first x bytes?
    private Random random = new Random();
    private float percentCorrupt = 0.01f;

    static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.OCTET_STREAM);

    @Override
    public Set<MediaType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public void transform(InputStream is, OutputStream os) throws IOException {
        //TODO -- don't load the full thing into memory
        byte[] input = IOUtils.toByteArray(is);
        if (input.length == 0) {
            return;
        }
        byte[] singleByte = new byte[1];
        //make sure that there's at least one change, even in short files
        int atLeastOneIndex = random.nextInt(input.length);

        for (int i = 0; i < input.length; i++) {
            if (random.nextFloat() <= percentCorrupt || i == atLeastOneIndex) {
                random.nextBytes(singleByte);
                os.write(singleByte[0]);
            } else {
                os.write(input[i]);
            }
        }
    }

    public void setPercentCorrupt(float percentCorrupt) {
        this.percentCorrupt = percentCorrupt;
    }
}
