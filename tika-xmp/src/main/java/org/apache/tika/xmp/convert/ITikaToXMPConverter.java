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
package org.apache.tika.xmp.convert;

import org.apache.tika.metadata.Metadata;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;

/**
 * Interface for the specific <code>Metadata</code> to XMP converters
 */
public interface ITikaToXMPConverter {
    /**
     * Converts a Tika {@link Metadata}-object into an {@link XMPMeta} containing the useful
     * properties.
     *
     * @param metadata
     *            a Tika Metadata object
     * @return Returns an XMPMeta object.
     * @throws XMPException
     *             If an error occurs during the creation of the XMP object.
     */
    XMPMeta process(Metadata metadata) throws XMPException;
}
