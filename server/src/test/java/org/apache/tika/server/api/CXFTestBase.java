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

package org.apache.tika.server.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.transport.common.gzip.GZIPInInterceptor;
import org.apache.cxf.transport.common.gzip.GZIPOutInterceptor;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.digestutils.CommonsDigester;
import org.apache.tika.server.DefaultInputStreamFactory;
import org.apache.tika.server.ServerStatus;
import org.apache.tika.server.TikaServerCli;
import org.apache.tika.server.api.TikaResourceApi;
import org.apache.tika.server.api.UnpackResourceApi;
import org.apache.tika.server.api.impl.TikaResourceApiServiceImpl;
import org.apache.tika.server.api.impl.UnpackResourceApiServiceImpl;
import org.junit.After;
import org.junit.Before;

public abstract class CXFTestBase {
    private final static int DIGESTER_READ_LIMIT = 20*1024*1024;

    protected static final String endPoint =
            "http://localhost:" + TikaServerCli.DEFAULT_PORT;
    protected Server server;
    private TikaConfig tika;

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
    public void setUp() throws Exception {

        this.tika = new TikaConfig(getTikaConfigInputStream());
        TikaResourceApiServiceImpl.init(tika,
                new CommonsDigester(DIGESTER_READ_LIMIT, "md5,sha1:32"),
                new DefaultInputStreamFactory(), new ServerStatus(true));
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        //set compression interceptors
        sf.setOutInterceptors(
                Collections.singletonList(new GZIPOutInterceptor())
        );
        sf.setInInterceptors(
                Collections.singletonList(new GZIPInInterceptor()));

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

    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream("tika-config-for-server-tests.xml");
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
        return IOUtils.toString(in, UTF_8);
    }

    protected Map<String, String> readZipArchive(InputStream inputStream) throws IOException {
        Map<String, String> data = new HashMap<String, String>();
        Path tempFile = writeTemporaryArchiveFile(inputStream, "zip");
        ZipFile zip = new ZipFile(tempFile.toFile());
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(zip.getInputStream(entry), bos);
            data.put(entry.getName(), DigestUtils.md5Hex(bos.toByteArray()));
        }
        zip.close();
        Files.delete(tempFile);
        return data;
    }

    protected String readArchiveText(InputStream inputStream) throws IOException {
        Path tempFile = writeTemporaryArchiveFile(inputStream, "zip");
        ZipFile zip = new ZipFile(tempFile.toFile());
        zip.getEntry(UnpackResourceApiServiceImpl.TEXT_FILENAME);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(zip.getInputStream(zip.getEntry(UnpackResourceApiServiceImpl.TEXT_FILENAME)), bos);

        zip.close();
        Files.delete(tempFile);
        return bos.toString(UTF_8.name());
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

    private Path writeTemporaryArchiveFile(InputStream inputStream, String archiveType) throws IOException {
        Path tmp = Files.createTempFile("apache-tika-server-test-tmp-", "."+archiveType);
        Files.copy(inputStream, tmp, StandardCopyOption.REPLACE_EXISTING);
        return tmp;
    }

    public static InputStream gzip(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream gz = new GzipCompressorOutputStream(bos);
        IOUtils.copy(is, gz);
        gz.flush();
        gz.close();
        return new ByteArrayInputStream(bos.toByteArray());
    }

}
