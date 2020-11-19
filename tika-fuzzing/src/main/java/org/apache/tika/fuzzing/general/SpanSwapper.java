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

/**
 * randomly swaps spans from the input
 *
 */
public class SpanSwapper implements Transformer {

    Random random = new Random();
    private float swapProbability = 0.01f;
    int maxSpanLength = 10000;

    static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.OCTET_STREAM);

    @Override
    public Set<MediaType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public void transform(InputStream is, OutputStream os) throws IOException {
        byte[] input = IOUtils.toByteArray(is);
        int numSwaps = (int) Math.floor(swapProbability*input.length);
        //at least one swap
        numSwaps = numSwaps == 0 ? 1 : numSwaps;
        byte[] ret = new byte[input.length];
        System.arraycopy(input, 0, ret, 0, input.length);
        for (int i = 0; i < numSwaps; i++) {
            ret = swap(ret);
        }
        os.write(ret);
    }

    private byte[] swap(byte[] ret) {
        if (ret.length == 0) {
            return new byte[0];
        }
        int srcStart = random.nextInt(ret.length);
        int targStart = random.nextInt(ret.length);
        //these spans can overlap;

        int len = random.nextInt(maxSpanLength);
        int maxStart = Math.max(srcStart, targStart);
        len = (len+maxStart < ret.length) ? len :
                ret.length-maxStart;

        byte[] landingBytes = new byte[len];
        //copy the landing zone
        System.arraycopy(ret, targStart, landingBytes, 0, len);
        //now copy the src onto the targ
        System.arraycopy(ret, srcStart, ret, targStart, len);
        //now copy the targ over to the src
        System.arraycopy(landingBytes, 0, ret, srcStart, len);
        return ret;
    }

}
