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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.IOUtils;
import org.apache.tika.server.resource.UnpackerResource;
import org.junit.After;
import org.junit.Before;

public abstract class CXFTestBase {
    protected static final String endPoint =
            "http://localhost:" + TikaServerCli.DEFAULT_PORT;
    protected Server server;
    protected TikaConfig tika;

    public static void assertContains(String needle, String haystack) {
        assertTrue(needle + " not found in:\n" + haystack, haystack.contains(needle));
    }

    public static void assertNotFound(String needle, String haystack) {
        assertFalse(needle + " unexpectedly found in:\n" + haystack, haystack.contains(needle));
    }

    protected static InputStream copy(InputStream in, int remaining) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (remaining > 0) {
            byte[] bytes = new byte[remaining];
            int n = in.read(bytes);
            if (n <= 0) {
                break;
            }
            out.write(bytes, 0, n);
            remaining -= n;
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    @Before
    public void setUp() {
        this.tika = TikaConfig.getDefaultConfig();

        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        setUpResources(sf);
        setUpProviders(sf);
        sf.setAddress(endPoint + "/");

        BindingFactoryManager manager = sf.getBus().getExtension(
                BindingFactoryManager.class
        );

        JAXRSBindingFactory factory = new JAXRSBindingFactory();
        factory.setBus(sf.getBus());

        manager.registerBindingFactory(
                JAXRSBindingFactory.JAXRS_BINDING_ID,
                factory
        );

        server = sf.create();
    }

    /**
     * Have the test do {@link JAXRSServerFactoryBean#setResourceClasses(Class...)}
     * and {@link JAXRSServerFactoryBean#setResourceProvider(Class, org.apache.cxf.jaxrs.lifecycle.ResourceProvider)}
     */
    protected abstract void setUpResources(JAXRSServerFactoryBean sf);

    /**
     * Have the test do {@link JAXRSServerFactoryBean#setProviders(java.util.List)}, if needed
     */
    protected abstract void setUpProviders(JAXRSServerFactoryBean sf);

    @After
    public void tearDown() throws Exception {
        server.stop();
        server.destroy();
    }

    protected String getStringFromInputStream(InputStream in) throws Exception {
        return IOUtils.toString(in);
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
        return bos.toString(IOUtils.UTF_8.name());
    }

    protected Map<String, String> readArchiveFromStream(ArchiveInputStream zip) throws IOException {
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

    private File writeTemporaryArchiveFile(InputStream inputStream, String archiveType) throws IOException {
        File tempFile = File.createTempFile("tmp-", "." + archiveType);
        IOUtils.copy(inputStream, new FileOutputStream(tempFile));
        return tempFile;
    }

}
