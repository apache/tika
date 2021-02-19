package org.apache.tika.pipes.driver;

import org.apache.tika.pipes.async.AsyncCli;
import org.junit.Test;

public class AsyncCliTest {
    @Test
    public void testBasic() throws Exception {
        String[] args = {
                "/Users/allison/Desktop/tika-tmp/tika-config.xml"
        };
        AsyncCli.main(args);
    }
}
