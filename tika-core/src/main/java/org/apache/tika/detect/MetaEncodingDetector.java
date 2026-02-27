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

/**
 * Marker interface for encoding detectors that arbitrate among
 * candidates collected by base detectors rather than detecting
 * encoding directly from the stream.
 *
 * <p>When a {@code MetaEncodingDetector} is present in a
 * {@link CompositeEncodingDetector}, the composite switches from
 * first-match-wins to collect-all mode: all base detectors run
 * first and their results are collected in an
 * {@link EncodingDetectorContext}, then the meta detector's
 * {@link #detect} method is called to pick the winner.</p>
 *
 * <p>The {@link EncodingDetectorContext} is placed in the
 * {@link org.apache.tika.parser.ParseContext} before the meta
 * detector is invoked, so implementations can retrieve it via
 * {@code parseContext.get(EncodingDetectorContext.class)}.</p>
 *
 * @since Apache Tika 3.2
 */
public interface MetaEncodingDetector extends EncodingDetector {
}
