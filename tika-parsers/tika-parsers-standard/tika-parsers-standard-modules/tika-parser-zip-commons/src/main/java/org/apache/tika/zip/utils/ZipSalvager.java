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
package org.apache.tika.zip.utils;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Zip;

public class ZipSalvager {

    private static final Logger LOG = LoggerFactory.getLogger(ZipSalvager.class);

    /**
     * Tries to open a ZipFile from the TikaInputStream. If direct opening fails,
     * attempts to salvage the ZIP and open the salvaged version.
     * <p>
     * On success:
     * <ul>
     *   <li>Sets {@link Zip#DETECTOR_ZIPFILE_OPENED} to true in metadata</li>
     *   <li>Stores the ZipFile in tis.openContainer (if not already set)</li>
     *   <li>Returns the opened ZipFile</li>
     * </ul>
     * On failure:
     * <ul>
     *   <li>Sets {@link Zip#DETECTOR_ZIPFILE_OPENED} to false in metadata</li>
     *   <li>Returns null</li>
     * </ul>
     *
     * @param tis      the TikaInputStream (must be file-backed)
     * @param metadata the metadata to update with hints
     * @param charset  optional charset for entry names (may be null)
     * @return the opened ZipFile, or null if opening and salvaging both failed
     */
    public static ZipFile tryToOpenZipFile(TikaInputStream tis, Metadata metadata, Charset charset) {
        // First, try direct open
        try {
            ZipFile.Builder builder = new ZipFile.Builder().setFile(tis.getFile());
            if (charset != null) {
                builder.setCharset(charset);
            }
            ZipFile zipFile = builder.get();

            // Direct open succeeded
            metadata.set(Zip.DETECTOR_ZIPFILE_OPENED, true);
            if (tis.getOpenContainer() == null) {
                tis.setOpenContainer(zipFile);
            } else {
                tis.addCloseableResource(zipFile);
            }
            return zipFile;
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("ZipFile failed to open directly; attempting to salvage", e);
            }
        }

        // Direct open failed - try salvaging
        try {
            final Path salvagedPath = Files.createTempFile("tika-salvaged-", ".zip");
            tis.enableRewind();
            salvageCopy(tis, salvagedPath, false);
            tis.rewind();

            ZipFile.Builder builder = new ZipFile.Builder().setPath(salvagedPath);
            if (charset != null) {
                builder.setCharset(charset);
            }
            ZipFile salvagedZip = builder.get();

            // Salvaging succeeded
            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully salvaged ZIP to {}", salvagedPath);
            }
            metadata.set(Zip.DETECTOR_ZIPFILE_OPENED, true);
            metadata.set(Zip.SALVAGED, true);

            // Add file deletion FIRST so it runs AFTER ZipFile is closed
            // (TemporaryResources uses LIFO order)
            tis.addCloseableResource(() -> {
                try {
                    Files.deleteIfExists(salvagedPath);
                } catch (IOException e) {
                    LOG.warn("Failed to delete salvaged temp file: {}", salvagedPath, e);
                    salvagedPath.toFile().deleteOnExit();
                }
            });

            // Then add ZipFile (will be closed before file deletion runs)
            if (tis.getOpenContainer() == null) {
                tis.setOpenContainer(salvagedZip);
            } else {
                tis.addCloseableResource(salvagedZip);
            }
            return salvagedZip;
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Salvaging failed", e);
            }
        }

        // Both direct open and salvaging failed
        metadata.set(Zip.DETECTOR_ZIPFILE_OPENED, false);
        return null;
    }

    /**
     * Tries to open a ZipFile from the TikaInputStream using default charset.
     *
     * @see #tryToOpenZipFile(TikaInputStream, Metadata, Charset)
     */
    public static ZipFile tryToOpenZipFile(TikaInputStream tis, Metadata metadata) {
        return tryToOpenZipFile(tis, metadata, null);
    }

    /**
     * Streams the broken zip and rebuilds a new zip that is at least a valid zip file.
     * The contents of the final stream may be truncated, but the result should be a valid zip file.
     * <p>
     * This does nothing fancy to fix the underlying broken zip.
     * <p>
     * This method does NOT close the TikaInputStream - the caller owns it.
     * The caller should call {@code tis.enableRewind()} before calling this method
     * if retry on DATA_DESCRIPTOR is needed.
     *
     * @param tis               the TikaInputStream to read from (not closed by this method)
     * @param salvagedZip       the output path for the salvaged ZIP
     * @param allowStoredEntries whether to allow stored entries with data descriptors
     * @throws IOException if salvaging fails
     */
    public static void salvageCopy(TikaInputStream tis, Path salvagedZip,
                                   boolean allowStoredEntries) throws IOException {
        try (ZipArchiveOutputStream outputStream = new ZipArchiveOutputStream(Files.newOutputStream(salvagedZip));
                ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(
                        CloseShieldInputStream.wrap(tis), "UTF8", false,
                        allowStoredEntries)) {
            ZipArchiveEntry zae = zipArchiveInputStream.getNextEntry();
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
            //now retry with data descriptor support
            if (!allowStoredEntries &&
                    e.getFeature() == UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR) {
                tis.rewind();
                salvageCopy(tis, salvagedZip, true);
            } else {
                throw e;
            }
        } catch (IOException e) {
            LOG.warn("problem fixing zip", e);
        }
    }

    /**
     * Streams a broken zip from a Path and rebuilds a valid zip file.
     * <p>
     * This is a convenience method that creates a TikaInputStream internally.
     *
     * @param brokenZip   the path to the broken ZIP file
     * @param salvagedZip the path for the salvaged ZIP output
     * @throws IOException if salvaging fails
     */
    public static void salvageCopy(Path brokenZip, Path salvagedZip) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(brokenZip)) {
            tis.enableRewind();
            salvageCopy(tis, salvagedZip, false);
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
            zae = zipArchiveInputStream.getNextEntry();
        }
    }
}
