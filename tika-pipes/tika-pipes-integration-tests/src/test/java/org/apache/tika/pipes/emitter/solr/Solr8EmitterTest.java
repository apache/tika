package org.apache.tika.pipes.emitter.solr;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class Solr8EmitterTest extends SolrEmitterTestBase {
    @Rule
    public GenericContainer<?> solr8 = new GenericContainer<>(DockerImageName.parse("solr:8"))
            .withExposedPorts(8983, 9983)
            .withCommand("-DzkRun");

    @Before
    public void setupTest() throws Exception {
        setupSolr(solr8);
    }

    @Test
    public void testEmitWithZkHost() throws Exception {
        runEmitTestWithZkHost();
    }

    @Test
    public void testEmitWithSolrUrls() throws Exception {
        runEmitTestWithSolrUrls();
    }
}
