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

import java.io.InputStream;

import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class DeprecatedStreamingZipContainerDetector extends ZipContainerDetectorBase
        implements Detector {

    private static final int MAX_MIME_TYPE = 1024;
    private static final int MAX_MANIFEST = 20 * 1024 * 1024;
    /*
     */

    /**
     * @param is inputstream to read from. Callers must mark/reset the stream
     *           before/after this call to detect.  This call does not close the stream!
     *           Depending on the file type, this call to detect may read the entire stream.
     *           Make sure to use a {@link org.apache.tika.io.BoundedInputStream} or similar
     *           if you want to protect against reading the entire stream.
     * @return
     */
    @Override
    public MediaType detect(InputStream is, Metadata metadata) {
/*
        Set<String> fileNames = new HashSet<>();
        Set<String> directoryNames = new HashSet<>();
        try (ZipArchiveInputStream zipArchiveInputStream =
                     new ZipArchiveInputStream(new CloseShieldInputStream(is))) {
            ZipArchiveEntry zae = zipArchiveInputStream.getNextZipEntry();
            while (zae != null) {
                String name = zae.getName();
                if (zae.isDirectory()) {
                    directoryNames.add(name);
                    zae = zipArchiveInputStream.getNextZipEntry();
                    continue;
                }
                fileNames.add(name);
                //we could also parse _rel/.rels, but if
                // there isn't a valid content_types, then POI
                //will throw an exception...Better to backoff to PKG
                //than correctly identify a truncated
                if (name.equals("[Content_Types].xml")) {
                    MediaType mt = parseOOXMLContentTypes(zipArchiveInputStream);
                    if (mt != null) {
                        return mt;
                    }
                    return TIKA_OOXML;
                } else if (IWorkPackageParser.IWORK_CONTENT_ENTRIES.contains(name)) {
                    IWorkPackageParser.IWORKDocumentType type = IWorkPackageParser.
                    IWORKDocumentType.detectType(zipArchiveInputStream);
                    if (type != null) {
                        return type.getType();
                    }
                } else if (name.equals("mimetype")) {
                    //can't rely on zae.getSize to determine if there is any
                    //content here. :(
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    BoundedInputStream bis = new BoundedInputStream(MAX_MIME_TYPE,
                    zipArchiveInputStream);
                    IOUtils.copy(bis, bos);
                    //do anything with an inputstream > MAX_MIME_TYPE?
                    if (bos.toByteArray().length > 0)  {
                        //odt -- TODO -- check that the results are valid
                        return MediaType.parse(new String(bos.toByteArray(), UTF_8));
                    }
                } else if (name.equals("META-INF/manifest.xml")) {
                    //for an unknown reason, passing in the zipArchiveInputStream
                    //"as is" can cause the iteration of the entries to stop early
                    //without exception or warning.  So, copy the full stream, then
                    //process.  TIKA-3061
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    BoundedInputStream bis = new BoundedInputStream(MAX_MANIFEST,
                     zipArchiveInputStream);
                    IOUtils.copy(bis, bos);
                    //TODO: do something if the full stream hasn't been read?
                    MediaType mt = detectStarOfficeX(new ByteArrayInputStream(bos.toByteArray()));
                    if (mt != null) {
                        return mt;
                    }
                }
                MediaType mt = IWork18PackageParser.IWork18DocumentType.detectIfPossible(zae);
                if (mt != null) {
                    return mt;
                }
                mt = IWork13PackageParser.IWork13DocumentType.detectIfPossible(zae);
                if (mt != null) {
                    return mt;
                }
                zae = zipArchiveInputStream.getNextZipEntry();
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //swallow
        }
        //entrynames is the union of directory names and file names
        Set<String> entryNames = new HashSet<>(fileNames);
        entryNames.addAll(directoryNames);
        MediaType mt = detectKmz(fileNames);
        if (mt != null) {
            return mt;
        }
        mt = detectJar(entryNames);
        if (mt != null) {
            return mt;
        }
        mt = detectIpa(entryNames);
        if (mt != null) {
            return mt;
        }
        mt = detectIWorks(entryNames);
        if (mt != null) {
            return mt;
        }
        int hits = 0;
        for (String s : OOXML_HINTS) {
            if (entryNames.contains(s)) {
                if (++hits > 2) {
                    return TIKA_OOXML;
                }
            }
        } */
        return MediaType.APPLICATION_ZIP;
    }
/*
    private static MediaType detectIWorks(Set<String> entryNames) {
        //general iworks
        if (entryNames.contains(IWorkPackageParser.IWORK_COMMON_ENTRY)) {
            return MediaType.application("vnd.apple.iwork");
        }
        return null;
    }


*/

}
