package org.apache.tika.pipes.emitter.solr;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class Solr7EmitterTest extends SolrEmitterTestBase {
    @Rule
    public GenericContainer<?> solr7 = new GenericContainer<>(DockerImageName.parse("solr:7"))
            .withExposedPorts(8983, 9983)
            .withCommand("-DzkRun");

    @Before
    public void setupTest() throws Exception {
        setupSolr(solr7);
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
