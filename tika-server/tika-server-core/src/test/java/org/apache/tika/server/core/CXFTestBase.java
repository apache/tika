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
package org.apache.tika.server.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.JsonConfigHelper;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.resource.UnpackerResource;

public abstract class CXFTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(CXFTestBase.class);

    public final static String FETCHER_ID = "fsf";
    public final static String EMITTER_JSON_ID = "fse-json";
    public final static String EMITTER_BYTES_ID = "fse-bytes";

    public final static String BASIC_CONFIG = """
            {
              "auto-detect-parser": {
                "outputThreshold": 1000000,
                "digesterFactory": {
                  "commons-digester-factory": {
                    "markLimit": 100000,
                    "digests": [
                      { "algorithm": "MD5" }
                    ]
                  }
                },
                "throwOnZeroBytes": false
              }
            }
            """;

    private static final String TEMPLATE_RESOURCE = "/configs/cxf-test-base-template.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final String endPoint = "http://localhost:" + TikaServerConfig.DEFAULT_PORT;
    protected final static int DIGESTER_READ_LIMIT = 20 * 1024 * 1024;
    protected Server server;
    protected TikaLoader tika;

    public static void createPluginsConfig(Path configPath, Path inputDir, Path jsonOutputDir, Path bytesOutputDir, Long timeoutMillis) throws IOException {

        Path pluginsDir = Paths.get("target/plugins");
        if (!Files.isDirectory(pluginsDir)) {
            LOG.warn("CAN'T FIND PLUGINS DIR. pwd={}", Paths.get("").toAbsolutePath().toString());
        }

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("FETCHER_BASE_PATH", inputDir);
        replacements.put("JSON_EMITTER_BASE_PATH", jsonOutputDir);
        replacements.put("PLUGINS_PATHS", pluginsDir);
        if (bytesOutputDir != null) {
            replacements.put("BYTES_EMITTER_BASE_PATH", bytesOutputDir);
        }
        replacements.put("TIMEOUT_MILLIS", timeoutMillis != null ? timeoutMillis : 10000L);

        JsonConfigHelper.writeConfigFromResource(TEMPLATE_RESOURCE,
                CXFTestBase.class, replacements, configPath);
    }


    public static void assertContains(String needle, String haystack) {
        assertTrue(haystack.contains(needle), needle + " not found in:\n" + haystack);
    }

    public static void assertNotFound(String needle, String haystack) {
        assertFalse(haystack.contains(needle), needle + " unexpectedly found in:\n" + haystack);
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

    protected static String getStringFromInputStream(InputStream in) throws IOException {
        return IOUtils.toString(in, UTF_8);
    }

    public static InputStream gzip(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream gz = new GzipCompressorOutputStream(bos);
        IOUtils.copy(is, gz);
        gz.flush();
        gz.close();
        return new ByteArrayInputStream(bos.toByteArray());
    }

    protected static AverageColor getAverageColor(BufferedImage image, int minX, int maxX, int minY, int maxY) {
        long totalRed = 0;
        long totalGreen = 0;
        long totalBlue = 0;
        int pixels = 0;
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                int clr = image.getRGB(x, y);
                int red = (clr & 0x00ff0000) >> 16;
                int green = (clr & 0x0000ff00) >> 8;
                int blue = clr & 0x000000ff;
                totalRed += red;
                totalGreen += green;
                totalBlue += blue;
                pixels++;
            }
        }
        return new AverageColor((double) totalRed / (double) pixels, (double) totalGreen / (double) pixels, (double) totalBlue / (double) pixels);
    }

    @BeforeEach
    public void setUp() throws Exception {
        Path tmp = Files.createTempFile("tika-server-test-", ".json");
        try {
            Files.copy(getTikaConfigInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
            this.tika = TikaLoader.load(tmp);
        } finally {
            Files.delete(tmp);
        }
        TikaServerConfig tikaServerConfig = getTikaServerConfig();
        TikaResource.init(tika, tikaServerConfig,
                getInputStreamFactory(getPipesConfigInputStream()),
                new ServerStatus());
        tika.loadAutoDetectParser();
        tika.loadMetadataFilters();
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        //set compression interceptors
        sf.setOutInterceptors(Collections.singletonList(new GZIPOutInterceptor()));
        sf.setInInterceptors(Collections.singletonList(new GZIPInInterceptor()));

        setUpResources(sf);
        setUpProviders(sf);
        sf.setAddress(endPoint + "/");
        sf.setResourceComparator(new ProduceTypeResourceComparator());

        BindingFactoryManager manager = sf
                .getBus()
                .getExtension(BindingFactoryManager.class);

        JAXRSBindingFactory factory = new JAXRSBindingFactory();
        factory.setBus(sf.getBus());

        manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
        server = sf.create();
    }

    protected TikaServerConfig getTikaServerConfig() {
        TikaServerConfig tikaServerConfig = new TikaServerConfig();
        tikaServerConfig.setReturnStackTrace(true);
        return tikaServerConfig;
    }

    protected InputStreamFactory getInputStreamFactory(InputStream tikaConfig) {
        return new DefaultInputStreamFactory();
    }

    protected InputStream getTikaConfigInputStream() throws IOException {
        return new ByteArrayInputStream(BASIC_CONFIG.getBytes(UTF_8));
    }

    protected InputStream getPipesConfigInputStream() throws IOException {
        if (getPipesInputPath() == null) {
            return null;
        }

        Path pluginsDir = Paths.get("target/plugins");
        if (!Files.isDirectory(pluginsDir)) {
            LOG.warn("CAN'T FIND PLUGINS DIR. pwd={}", Paths
                    .get("")
                    .toAbsolutePath()
                    .toString());
        }

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("FETCHER_BASE_PATH", getPipesInputPath());
        replacements.put("PLUGINS_PATHS", pluginsDir);
        replacements.put("TIMEOUT_MILLIS", 10000L);

        JsonNode config = JsonConfigHelper.loadFromResource(TEMPLATE_RESOURCE,
                CXFTestBase.class, replacements);
        String json = MAPPER.writeValueAsString(config);
        return new ByteArrayInputStream(json.getBytes(UTF_8));
    }

    protected String getPipesInputPath() {
        return null;
    }

    /**
     * Have the test do {@link JAXRSServerFactoryBean#setResourceClasses(Class...)}
     * and {@link JAXRSServerFactoryBean#setResourceProvider(Class,
     * org.apache.cxf.jaxrs.lifecycle.ResourceProvider)}
     */
    protected abstract void setUpResources(JAXRSServerFactoryBean sf);

    /**
     * Have the test do {@link JAXRSServerFactoryBean#setProviders(java.util.List)}, if needed
     */
    protected abstract void setUpProviders(JAXRSServerFactoryBean sf);

    @AfterEach
    public void tearDown() throws Exception {
        server.stop();
        server.destroy();
    }

    protected Map<String, String> readZipArchive(InputStream inputStream) throws IOException {
        Map<String, String> data = new HashMap<>();
        Path tempFile = null;
        try {
            tempFile = writeTemporaryArchiveFile(inputStream, "zip");
            try (ZipFile zip = ZipFile.builder().setPath(tempFile).get())
            {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry entry = entries.nextElement();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    IOUtils.copy(zip.getInputStream(entry), bos);
                    data.put(entry.getName(), DigestUtils.md5Hex(bos.toByteArray()));
                }
            }
        } finally {
            if (tempFile != null) {
                Files.delete(tempFile);
            }
        }
        return data;
    }

    protected Map<String, byte[]> readZipArchiveBytes(InputStream inputStream) throws IOException {
        Map<String, byte[]> data = new HashMap<>();
        Path tempFile = null;
        try {
            tempFile = writeTemporaryArchiveFile(inputStream, "zip");
            try (ZipFile zip = ZipFile.builder().setPath(tempFile).get())
            {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry entry = entries.nextElement();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    IOUtils.copy(zip.getInputStream(entry), bos);
                    data.put(entry.getName(), bos.toByteArray());
                }
            }
        } finally {
            if (tempFile != null) {
                Files.delete(tempFile);
            }
        }
        return data;
    }

    protected String readArchiveText(InputStream inputStream) throws IOException {
        Path tempFile = writeTemporaryArchiveFile(inputStream, "zip");
        ByteArrayOutputStream bos;
        try (ZipFile zip = ZipFile.builder().setPath(tempFile).get())
        {
            zip.getEntry(UnpackerResource.TEXT_FILENAME);
            bos = new ByteArrayOutputStream();
            IOUtils.copy(zip.getInputStream(zip.getEntry(UnpackerResource.TEXT_FILENAME)), bos);
        }
        Files.delete(tempFile);
        return bos.toString(UTF_8.name());
    }

    protected String readArchiveMetadataAndText(InputStream inputStream) throws IOException {
        Path tempFile = writeTemporaryArchiveFile(inputStream, "zip");
        String metadata;
        String txt;
        try (ZipFile zip = ZipFile.builder().setPath(tempFile).get())
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            zip.getEntry(UnpackerResource.META_FILENAME);
            IOUtils.copy(zip.getInputStream(zip.getEntry(UnpackerResource.META_FILENAME)), bos);
            metadata = new String(bos.toByteArray(), UTF_8);
            bos = new ByteArrayOutputStream();
            zip.getEntry(UnpackerResource.TEXT_FILENAME);
            IOUtils.copy(zip.getInputStream(zip.getEntry(UnpackerResource.TEXT_FILENAME)), bos);
            txt = new String(bos.toByteArray(), UTF_8);
        }
        Files.delete(tempFile);
        return metadata + "\n\n" + txt;
    }

    protected Map<String, String> readArchiveFromStream(ArchiveInputStream zip) throws IOException {
        Map<String, String> data = new HashMap<>();
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
        Path tmp = Files.createTempFile("apache-tika-server-test-tmp-", "." + archiveType);
        Files.copy(inputStream, tmp, StandardCopyOption.REPLACE_EXISTING);
        return tmp;
    }

    public static class AverageColor {
        double red;
        double green;
        double blue;

        public AverageColor(double averageRed, double averageGreen, double averageBlue) {
            this.red = averageRed;
            this.green = averageGreen;
            this.blue = averageBlue;
        }

        public double getRed() {
            return red;
        }

        public double getGreen() {
            return green;
        }

        public double getBlue() {
            return blue;
        }

        @Override
        public String toString() {
            return "AverageColor{" + "red=" + red + ", green=" + green + ", blue=" + blue + '}';
        }
    }
}
