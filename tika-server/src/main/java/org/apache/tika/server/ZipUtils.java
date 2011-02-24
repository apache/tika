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

import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipException;
import java.util.UUID;

public class ZipUtils {
  private ZipUtils() {
  }

  public static void zipStoreBuffer(ZipOutputStream zip, String name, byte[] dataBuffer) throws IOException {
    ZipEntry zipEntry = new ZipEntry(name!=null?name: UUID.randomUUID().toString());
    zipEntry.setMethod(ZipOutputStream.STORED);

    zipEntry.setSize(dataBuffer.length);
    CRC32 crc32 = new CRC32();
    crc32.update(dataBuffer);
    zipEntry.setCrc(crc32.getValue());

    try {
      zip.putNextEntry(zipEntry);
    } catch (ZipException ex) {
      if (name!=null) {
        zipStoreBuffer(zip, null, dataBuffer);
        return;
      }
    }

    zip.write(dataBuffer);

    zip.closeEntry();
  }

  public static String cleanupFilename(String name) {
    if (name.charAt(0)=='/') {
      name = name.substring(1);
    }

    return name;
  }
}
