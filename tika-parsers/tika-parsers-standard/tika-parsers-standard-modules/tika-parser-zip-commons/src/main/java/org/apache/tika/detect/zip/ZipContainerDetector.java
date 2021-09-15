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
package org.apache.tika.detect.zip;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

/**
 * Classes that implement this must be able to detect on a ZipFile and in streaming mode.
 * In streaming mode, each ziparchiventry is "updated" and then
 * {@link #streamingDetectFinal(StreamingDetectContext)} is
 * called for a final decision.
 * <p>
 * During streaming detection, state is stored in the StreamingDetectContext
 */
public interface ZipContainerDetector extends Serializable {

    /**
     * If detection is successful, the ZipDetector should set the zip
     * file or OPCPackage in TikaInputStream.setOpenContainer()
     *
     * Implementations should _not_ close the ZipFile
     *
     * @param zipFile
     * @param tis
     * @return
     * @throws IOException
     */
    MediaType detect(ZipFile zipFile, TikaInputStream tis) throws IOException;

    /**
     * Try to detect on a specific entry.  Detectors are allowed to store
     * state (e.g. "remember what they've seen") in the {@link StreamingDetectContext}
     *
     * @param zae
     * @return
     */
    MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                    StreamingDetectContext detectContext) throws IOException;

    /**
     * After we've finished streaming the zip archive entries,
     * a detector may make a final decision.
     *
     * @return
     */
    MediaType streamingDetectFinal(StreamingDetectContext detectContext);

}
