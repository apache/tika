package org.apache.tika.fetcher.s3;

import com.amazonaws.regions.Regions;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.fetcher.DefaultFetcher;
import org.apache.tika.fetcher.Fetcher;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class S3FetcherTest {

    @Test
    public void testBasic() throws Exception {
        TikaConfig config = new TikaConfig(
                S3FetcherTest.class.getResourceAsStream("/org/apache/tika/fetcher/s3/S3TikaConfig.xml"));
        Fetcher defaultFetcher = config.getFetcher();
        for (Fetcher fetcher : ((DefaultFetcher)defaultFetcher).getFetchers()) {
            if (fetcher instanceof S3Fetcher) {
                S3Fetcher s3Fetcher = (S3Fetcher) fetcher;
                assertEquals(Regions.US_WEST_2, s3Fetcher.getRegion());
                assertEquals("myKey", s3Fetcher.getKey());
                assertEquals("myBucket", s3Fetcher.getBucket());
            }
        }
    }
}
