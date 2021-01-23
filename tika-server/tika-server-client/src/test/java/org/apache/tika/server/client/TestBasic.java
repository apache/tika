package org.apache.tika.server.client;

import org.junit.Test;

public class TestBasic {

    @Test
    public void testBasic() throws Exception {
        String[] args = new String[]{
                "/Users/allison/Intellij/tika-main/tika-server/tika-server-client/src/test/resources/tika-config-simple-solr-emitter.xml",
                "http://localhost:9998/",
                "fs"
        };
        TikaClientCLI.main(args);
    }
}
