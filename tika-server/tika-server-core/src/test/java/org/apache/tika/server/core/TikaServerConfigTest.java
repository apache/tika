package org.apache.tika.server.core;

import org.apache.tika.config.TikaConfigTest;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class TikaServerConfigTest {

    @Test
    public void testBasic() throws Exception {
        TikaServerConfig config = TikaServerConfig.load(
                TikaConfigTest.class.getResourceAsStream("/configs/tika-config-server.xml"));
        assertEquals(-1, config.getMaxRestarts());
        assertEquals(54321, config.getTaskTimeoutMillis());
        assertEquals(true, config.isEnableUnsecureFeatures());
    }
}
