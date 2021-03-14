package org.apache.tika.pipes.fetchiterator.solr;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class Solr6FetchIteratorTest extends SolrFetchIteratorTestBase {

    @Rule
    public GenericContainer<?> solr6 = new GenericContainer<>(DockerImageName.parse("solr:6"))
            .withExposedPorts(8983, 9983)
            .withCommand("-DzkRun");

    @Before
    public void setupTest() throws Exception {
        setupSolr(solr6);
    }

    @Test
    public void testFetchIteratorWithSolrUrls() throws Exception {
        runSolrToFsWithSolrUrlsTest();
    }

    @Test
    public void testFetchIteratorWithZkHost() throws Exception {
        runSolrToFsWithZkHostTest();
    }
}
