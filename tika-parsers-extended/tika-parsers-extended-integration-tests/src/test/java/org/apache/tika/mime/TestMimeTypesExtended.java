package org.apache.tika.mime;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestMimeTypesExtended {

    MimeTypes repo;

    @Before
    public void setUp() throws Exception {
        TikaConfig config = TikaConfig.getDefaultConfig();
        repo = config.getMimeRepository();
    }

    @Test
    public void testNetCDF() throws Exception {
        assertTypeByData("application/x-netcdf", "sresa1b_ncar_ccsm3_0_run1_200001.nc");
    }

    private void assertTypeByData(String expected, String filename)
            throws IOException {
        try (InputStream stream = TikaInputStream.get(
                TestMimeTypesExtended.class.getResourceAsStream(
                "/test-documents/" + filename))) {
            assertNotNull("Test file not found: " + filename, stream);
            Metadata metadata = new Metadata();
            assertEquals(expected, repo.detect(stream, metadata).toString());
        }
    }
}
