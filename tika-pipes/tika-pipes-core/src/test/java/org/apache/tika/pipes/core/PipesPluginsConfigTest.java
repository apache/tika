package org.apache.tika.pipes.core;

import org.junit.jupiter.api.Test;

public class PipesPluginsConfigTest {

    @Test
    public void testBasic() throws Exception {
        PipesPluginsConfig pipesPluginsConfig =
                PipesPluginsConfig.load(PipesPluginsConfigTest.class.getResourceAsStream("/configs/fetchers.json"));
    }
}
