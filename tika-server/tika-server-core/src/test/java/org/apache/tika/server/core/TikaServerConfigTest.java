package org.apache.tika.server.core;

import org.apache.tika.config.TikaConfigTest;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TikaServerConfigTest {

    @Test
    public void testBasic() throws Exception {
        Set<String> settings = new HashSet<>();
        TikaServerConfig config = TikaServerConfig.load(
                TikaConfigTest.class.getResourceAsStream(
                        "/configs/tika-config-server.xml"), settings);
        assertEquals(-1, config.getMaxRestarts());
        assertEquals(54321, config.getTaskTimeoutMillis());
        assertEquals(true, config.isEnableUnsecureFeatures());

        assertTrue(settings.contains("taskTimeoutMillis"));
        assertTrue(settings.contains("enableUnsecureFeatures"));
    }
}
