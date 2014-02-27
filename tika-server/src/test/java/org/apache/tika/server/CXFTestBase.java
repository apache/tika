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

package org.apache.tika.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.cxf.io.CachedOutputStream;

public class CXFTestBase {

	protected String getStringFromInputStream(InputStream in) throws Exception {
		CachedOutputStream bos = new CachedOutputStream();
		IOUtils.copy(in, bos);
		in.close();
		bos.close();
		return bos.getOut().toString();
	}

	protected Map<String, String> readZipArchive(InputStream inputStream) throws IOException {
		Map<String, String> data = new HashMap<String, String>();
		File tempFile = writeTemporaryArchiveFile(inputStream, "zip");
        ZipFile zip = new ZipFile(tempFile);
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
          ZipArchiveEntry entry = entries.nextElement();
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          IOUtils.copy(zip.getInputStream(entry), bos);
          data.put(entry.getName(), DigestUtils.md5Hex(bos.toByteArray()));
        }

        zip.close();
        tempFile.delete();
		return data;
	}

	protected String readArchiveText(InputStream inputStream) throws IOException {
	    File tempFile = writeTemporaryArchiveFile(inputStream, "zip");
	    ZipFile zip = new ZipFile(tempFile);
	    zip.getEntry(UnpackerResource.TEXT_FILENAME);
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(zip.getInputStream(zip.getEntry(UnpackerResource.TEXT_FILENAME)), bos);

        zip.close();
        tempFile.delete();
		return bos.toString("UTF-8");
	}

	private File writeTemporaryArchiveFile(InputStream inputStream, String archiveType) throws IOException {
	  File tempFile = File.createTempFile("tmp-", "." + archiveType);
      IOUtils.copy(inputStream, new FileOutputStream(tempFile));
      return tempFile;
	}
}
