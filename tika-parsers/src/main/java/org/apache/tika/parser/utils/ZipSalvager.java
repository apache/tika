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
package org.apache.tika.parser.utils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.tika.io.IOUtils;
import org.apache.tika.utils.RereadableInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZipSalvager {

    private static final Logger LOG = LoggerFactory.getLogger(ZipSalvager.class);

    /**
     * This streams the broken zip and rebuilds a new zip that
     * is at least a valid zip file.  The contents of the final stream
     * may be truncated, but the result should be a valid zip file.
     * <p>
     * This does nothing fancy to fix the underlying broken zip.
     *
     * @param brokenZip
     * @param salvagedZip
     */
    public static void salvageCopy(InputStream brokenZip, File salvagedZip, boolean allowStoredEntries) throws IOException {
        if (!(brokenZip instanceof RereadableInputStream)) {
            brokenZip = new RereadableInputStream(brokenZip, 50000,
                    true, false);
        }
        try {
            try (ZipArchiveOutputStream outputStream = new ZipArchiveOutputStream(salvagedZip);
                ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(brokenZip,
                        "UTF8", false, allowStoredEntries)) {
                ZipArchiveEntry zae = zipArchiveInputStream.getNextZipEntry();
                try {
                    processZAE(zae, zipArchiveInputStream, outputStream);
                } catch (UnsupportedZipFeatureException uzfe) {
                    if (uzfe.getFeature() ==
                            UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR) {
                        //percolate up to allow for retry
                        throw uzfe;
                    }
                    //else swallow
                } catch (ZipException | EOFException e) {
                    //swallow
                }
                outputStream.flush();
                outputStream.finish();
            } catch (UnsupportedZipFeatureException e) {
                //percolate up to allow for retry
                throw e;
            } catch (IOException e) {
                LOG.warn("problem fixing zip", e);
            }
        } catch (UnsupportedZipFeatureException e) {
            //now retry
            if (allowStoredEntries == false) {
                ((RereadableInputStream) brokenZip).rewind();
                salvageCopy(brokenZip, salvagedZip, true);
            }
        }
    }

    private static void processZAE(ZipArchiveEntry zae, ZipArchiveInputStream zipArchiveInputStream,
                                   ZipArchiveOutputStream outputStream) throws IOException {
        while (zae != null) {
            if (!zae.isDirectory() && zipArchiveInputStream.canReadEntryData(zae)) {
                //create a new ZAE and copy over only the name so that
                //if there is bad info (e.g. CRC) in brokenZip's zae, that
                //won't be propagated or cause an exception
                outputStream.putArchiveEntry(new ZipArchiveEntry(zae.getName()));
                //this will copy an incomplete stream...so there
                //could be truncation of the xml/contents, but the zip file
                //should be intact.
                boolean successfullyCopied = false;
                try {
                    IOUtils.copy(zipArchiveInputStream, outputStream);
                    successfullyCopied = true;
                } catch (IOException e) {
                    //this can hit a "truncated ZipFile" IOException
                }
                outputStream.flush();
                outputStream.closeArchiveEntry();
                if (!successfullyCopied) {
                    break;
                }
            }
            zae = zipArchiveInputStream.getNextZipEntry();
        }
    }

    public static void salvageCopy(File brokenZip, File salvagedZip) throws IOException {
        try (InputStream is = Files.newInputStream(brokenZip.toPath())) {
            salvageCopy(is, salvagedZip, false);
        }
    }
}
