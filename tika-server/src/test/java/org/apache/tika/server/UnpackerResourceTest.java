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

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.test.framework.JerseyTest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.tika.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class UnpackerResourceTest extends JerseyTest {
  private static final String UNPACKER_PATH = "/unpacker";
  private static final String ALL_PATH = "/all";

  private static final String TEST_DOC_WAV = "Doc1_ole.doc";
  private static final String WAV1_MD5 = "bdd0a78a54968e362445364f95d8dc96";
  private static final String WAV1_NAME = "_1310388059/MSj00974840000[1].wav";
  private static final String WAV2_MD5 = "3bbd42fb1ac0e46a95350285f16d9596";
  private static final String WAV2_NAME = "_1310388058/MSj00748450000[1].wav";
  private static final String APPLICATION_MSWORD = "application/msword";
  private static final int NO_CONTENT = 204;
  private static final String JPG_NAME = "image1.jpg";
  private static final String XSL_IMAGE1_MD5 = "68ead8f4995a3555f48a2f738b2b0c3d";
  private static final String JPG_MD5 = XSL_IMAGE1_MD5;
  private static final String JPG2_NAME = "image2.jpg";
  private static final String JPG2_MD5 = "b27a41d12c646d7fc4f3826cf8183c68";
  private static final String TEST_DOCX_IMAGE = "2pic.docx";
  private static final String DOCX_IMAGE1_MD5 = "5516590467b069fa59397432677bad4d";
  private static final String DOCX_IMAGE2_MD5 = "a5dd81567427070ce0a2ff3e3ef13a4c";
  private static final String DOCX_IMAGE1_NAME = "image1.jpeg";
  private static final String DOCX_IMAGE2_NAME = "image2.jpeg";
  private static final String DOCX_EXE1_MD5 = "d71ffa0623014df725f8fd2710de4411";
  private static final String DOCX_EXE1_NAME = "GMapTool.exe";
  private static final String DOCX_EXE2_MD5 = "2485435c7c22d35f2de9b4c98c0c2e1a";
  private static final String DOCX_EXE2_NAME = "Setup.exe";
  private static final String XSLX_IMAGE1_NAME = "image1.jpeg";
  private static final String XSLX_IMAGE2_NAME = "image2.jpeg";
  private static final String XSL_IMAGE2_MD5 = "8969288f4245120e7c3870287cce0ff3";
  private static final String COVER_JPG_MD5SUM = "4d236dab6e711735ed11686641b1fba9";
  private static final String COVER_JPG = "cover.jpg";
  private static final String APPLICATION_XML = "application/xml";
  private static final String CONTENT_TYPE = "Content-type";

  public UnpackerResourceTest() throws Exception {
    super("org.apache.tika.server");
  }

  @Test
  public void testDocWAV() throws Exception {
    InputStream is =
            resource()
                    .path(UNPACKER_PATH)
                    .type(APPLICATION_MSWORD)
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

    ArchiveInputStream zip = new ZipArchiveInputStream(is);

    Map<String, String> data = readArchive(zip);

    assertEquals(WAV1_MD5, data.get(WAV1_NAME));
    assertEquals(WAV2_MD5, data.get(WAV2_NAME));

    assertFalse(data.containsKey(UnpackerResource.TEXT_FILENAME));
  }

  @Test
  public void testDocWAVText() throws Exception {
    InputStream is =
            resource()
                    .path(ALL_PATH)
                    .type(APPLICATION_MSWORD)
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

    ArchiveInputStream zip = new ZipArchiveInputStream(is);

    Map<String, String> data = readArchive(zip);

    assertEquals(WAV1_MD5, data.get(WAV1_NAME));
    assertEquals(WAV2_MD5, data.get(WAV2_NAME));

    assertTrue(data.containsKey(UnpackerResource.TEXT_FILENAME));
  }

  @Test
  public void testDocPicture() throws Exception {
    InputStream is =
            resource()
                    .path(UNPACKER_PATH)
                    .type(APPLICATION_MSWORD)
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

    ZipArchiveInputStream zip = new ZipArchiveInputStream(is);

    Map<String, String> data = readArchive(zip);

    assertEquals(JPG_MD5, data.get(JPG_NAME));
  }

  @Test
  public void testDocPictureNoOle() throws Exception {
    InputStream is =
            resource()
                    .path(UNPACKER_PATH)
                    .type(APPLICATION_MSWORD)
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream("2pic.doc"));

    ZipArchiveInputStream zip = new ZipArchiveInputStream(is);

    Map<String, String> data = readArchive(zip);

    assertEquals(JPG2_MD5, data.get(JPG2_NAME));
  }

  @Test
  public void testImageDOCX() throws Exception {
    InputStream is =
            resource()
                    .path(UNPACKER_PATH)
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream(TEST_DOCX_IMAGE));

    ZipArchiveInputStream zip = new ZipArchiveInputStream(is);

    Map<String, String> data = readArchive(zip);

    assertEquals(DOCX_IMAGE1_MD5, data.get(DOCX_IMAGE1_NAME));
    assertEquals(DOCX_IMAGE2_MD5, data.get(DOCX_IMAGE2_NAME));
  }

  @Test
  public void test415() throws Exception {
    ClientResponse cr =
            resource()
                    .path(UNPACKER_PATH)
                    .type("xxx/xxx")
                    .put(ClientResponse.class, ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

    assertEquals(415, cr.getStatus());
  }

  @Test
  public void testExeDOCX() throws Exception {
    String TEST_DOCX_EXE = "2exe.docx";
    InputStream is =
            resource()
                    .path(UNPACKER_PATH)
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream(TEST_DOCX_EXE));

    ZipArchiveInputStream zip = new ZipArchiveInputStream(is);

    Map<String, String> data = readArchive(zip);

    assertEquals(DOCX_EXE1_MD5, data.get(DOCX_EXE1_NAME));
    assertEquals(DOCX_EXE2_MD5, data.get(DOCX_EXE2_NAME));
  }

  @Test
  public void testImageXSL() throws Exception {
    InputStream is =
            resource()
                    .path(UNPACKER_PATH)
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream("pic.xls"));

    ZipArchiveInputStream zip = new ZipArchiveInputStream(is);

    Map<String, String> data = readArchive(zip);

    assertEquals(XSL_IMAGE1_MD5, data.get("0.jpg"));
    assertEquals(XSL_IMAGE2_MD5, data.get("1.jpg"));
  }

  private static Map<String, String> readArchive(ArchiveInputStream zip) throws IOException {
    Map<String, String> data = new HashMap<String, String>();

    while (true) {
      ArchiveEntry entry = zip.getNextEntry();

      if (entry==null) {
        break;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream();

      IOUtils.copy(zip, bos);

      data.put(entry.getName(), DigestUtils.md5Hex(bos.toByteArray()));
    }

    return data;
  }

  private static String readArchiveText(ArchiveInputStream zip) throws IOException {
    while (true) {
      ArchiveEntry entry = zip.getNextEntry();

      if (entry==null) {
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

  @Test
  public void testTarDocPicture() throws Exception {
    InputStream is =
            resource()
                    .path(UNPACKER_PATH)
                    .type(APPLICATION_MSWORD)
                    .accept("application/x-tar")
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

    ArchiveInputStream zip = new TarArchiveInputStream(is);

    Map<String, String> data = readArchive(zip);

    assertEquals(JPG_MD5, data.get(JPG_NAME));
  }

  @Test
  public void testText() throws IOException {
    InputStream is
            = resource()
                    .path(ALL_PATH)
                    .header(CONTENT_TYPE, APPLICATION_XML)
                    .put(InputStream.class, ClassLoader.getSystemResourceAsStream("test.doc"));
    String responseMsg = readArchiveText(new ZipArchiveInputStream(is));

    assertNotNull(responseMsg);
    assertTrue(responseMsg.contains("test"));
  }
}
