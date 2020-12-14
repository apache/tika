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
package org.apache.tika.fuzzing.pdf;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.tika.fuzzing.Transformer;
import org.apache.tika.fuzzing.general.ByteDeleter;
import org.apache.tika.fuzzing.general.ByteFlipper;
import org.apache.tika.fuzzing.general.ByteInjector;
import org.apache.tika.fuzzing.general.GeneralTransformer;
import org.apache.tika.fuzzing.general.SpanSwapper;
import org.apache.tika.fuzzing.general.Truncator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PDFTransformerConfig {

    private final Random random = new Random();

    private float randomizeObjectNumbers = -1.0f;

    private float randomizeRefNumbers = -1.0f;

    private int maxFilters = 1;
    private int minFilters = 1;

    private long maxFilteredStreamLength = -1;

    private Set<COSName> allowableFilters = new HashSet<>();

    private Transformer streamTransformer = new GeneralTransformer(1,
            new ByteDeleter(),
            new ByteFlipper(), new ByteInjector(), new SpanSwapper(), new Truncator());

    private Transformer unfilteredStreamTransformer = new GeneralTransformer(1,
            new ByteDeleter(),
            new ByteFlipper(), new ByteInjector(), new SpanSwapper(), new Truncator());

    public float getRandomizeObjectNumbers() {
        return randomizeObjectNumbers;
    }

    /**
     *
     * @param randomizeObjectNumbers probability that a given object number will be randomized.
     *                               If < 0, this will be ignored.
     */
    public void setRandomizeObjectNumbers(float randomizeObjectNumbers) {
        this.randomizeObjectNumbers = randomizeObjectNumbers;
    }

    /**
     *
     * @param randomizeRefNumbers probability that a given reference number will be randomized.
     *                            If < 0, this will be ignored.
     */
    public void setRandomizeRefNumbers(float randomizeRefNumbers) {
        this.randomizeRefNumbers = randomizeRefNumbers;
    }

    public float getRandomizeRefNumbers() {
        return randomizeRefNumbers;
    }

    public Transformer getUnfilteredStreamTransformer() {
        return unfilteredStreamTransformer;
    }

    public Transformer getStreamTransformer() {
        return streamTransformer;
    }

    /**
     * This transformer is applied to the stream _after_ each filter has been applied.
     *
     * @param transformer
     */
    public void setStreamTransformer(Transformer transformer) {
        this.streamTransformer = transformer;
    }

    /**
     * This transformer is applied to the stream _before_ any filters
     * are applied.
     * @param transformer
     */
    public void setUnfilteredStreamTransformer(Transformer transformer) {
        this.unfilteredStreamTransformer = transformer;
    }

    /**
     *
     * @param maxFilters maximum number of filters to apply
     */
    public void setMaxFilters(int maxFilters) {
        this.maxFilters = maxFilters;
    }

    /**
     * Which filters are allowed
     * @return
     */
    public Set<COSName> getAllowableFilters() {
        return allowableFilters;
    }

    public void setAllowableFilters(Set<COSName> allowableFilters) {
        this.allowableFilters = allowableFilters;
    }

    /**
     * If {@link #maxFilters} &gt; 0, this will randomly select filters given
     * the {@link #maxFilters} and {@link #minFilters}.  If {@link #maxFilters} < 0,
     * this will return the existing filters.
     *
     * @param existingFilters
     * @return
     */
    public List<COSName> getFilters(COSBase existingFilters) {
        if (maxFilters < 0) {
            List<COSName> ret = new ArrayList<>();
            if (existingFilters instanceof COSArray) {
                for (COSBase obj : ((COSArray)existingFilters)) {
                    ret.add((COSName)obj);
                }
            } else if (existingFilters instanceof COSName) {
                ret.add((COSName)existingFilters);
            }
            return ret;
        }

        int numFilters;
        if (maxFilters-minFilters == 0) {
            numFilters = maxFilters;
        } else {
            numFilters = minFilters + random.nextInt(maxFilters - minFilters);
        }

        List<COSName> allowable = new ArrayList<>(allowableFilters);

        List<COSName> filters = new ArrayList<>();
        for (int i = 0; i < numFilters; i++) {
            int index = random.nextInt(allowable.size());
            filters.add(allowable.get(index));
        }
        return filters;
    }

    /**
     * Minimum number of filters to apply to streams.
     *
     * @param minFilters
     */
    public void setMinFilters(int minFilters) {
        this.minFilters = minFilters;
    }

    public long getMaxFilteredStreamLength() {
        return maxFilteredStreamLength;
    }

    /**
     * Maximum filtered stream length.  AsciiHex doubles the size of the stream with
     * each encoding.  This is used as a circuit breaker to stop adding filters
     * if the stream goes above a given length.
     *
     * @param maxFilteredStreamLength
     */
    public void setMaxFilteredStreamLength(long maxFilteredStreamLength) {
        this.maxFilteredStreamLength = maxFilteredStreamLength;
    }
}
