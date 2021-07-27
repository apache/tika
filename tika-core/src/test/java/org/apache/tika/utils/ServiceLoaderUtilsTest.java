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
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.custom.detect.MyCustomDetector;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EmptyDetector;
import org.apache.tika.detect.FileCommandDetector;
import org.apache.tika.detect.OverrideDetector;
import org.apache.tika.detect.ZeroSizeFileDetector;

public class ServiceLoaderUtilsTest {

    @Test
    public void testSort() throws Exception {
        //OverrideDetector is moved to index 0
        //by the private service loading in DefaultDetector.
        //This tests that a custom detector always comes first
        //and then reverse alphabetical order
        Detector[] detectors = new Detector[]{new MyCustomDetector(), new EmptyDetector(),
                new FileCommandDetector(), new OverrideDetector(), new ZeroSizeFileDetector()};
        List<Detector> expected = Arrays.asList(detectors);
        List<Detector> shuffled = new ArrayList<>(expected);
        Random random = new Random(42);
        for (int i = 0; i < 10; i++) {
            Collections.shuffle(shuffled, random);
            ServiceLoaderUtils.sortLoadedClasses(shuffled);
            assertEquals(expected, shuffled, "failed on iteration " + i);
        }
    }


}
