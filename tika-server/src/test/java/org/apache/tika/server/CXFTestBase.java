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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import junit.framework.TestCase;

public class CXFTestBase extends TestCase {

	protected String getStringFromInputStream(InputStream in) throws Exception {
		CachedOutputStream bos = new CachedOutputStream();
		IOUtils.copy(in, bos);
		in.close();
		bos.close();
		return bos.getOut().toString();
	}

	protected Map<String, String> readArchive(ArchiveInputStream zip)
			throws IOException {
		Map<String, String> data = new HashMap<String, String>();

		while (true) {
			ArchiveEntry entry = zip.getNextEntry();
			if (entry == null) {
				break;
			}
			
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			IOUtils.copy(zip, bos);
			data.put(entry.getName(), DigestUtils.md5Hex(bos.toByteArray()));
		}

		return data;
	}

	protected String readArchiveText(ArchiveInputStream zip) throws IOException {
		while (true) {
			ArchiveEntry entry = zip.getNextEntry();
			if (entry == null) {
				break;
			}

			if (!entry.getName().equals(UnpackerResource.TEXT_FILENAME)) {
				continue;
			}

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			IOUtils.copy(zip, bos);
			return bos.toString("UTF-8");
		}

		return null;
	}
}
