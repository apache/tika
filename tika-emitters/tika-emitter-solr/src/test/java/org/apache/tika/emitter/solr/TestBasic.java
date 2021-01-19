package org.apache.tika.emitter.solr;


import org.apache.tika.config.TikaConfig;
import org.apache.tika.emitter.Emitter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.junit.Test;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class TestBasic {

    @Test
    public void testBasic() throws Exception {
        TikaConfig tikaConfig = new TikaConfig(
                TestBasic.class.getResourceAsStream("/tika-config-simple-emitter.xml"));
        Emitter emitter = tikaConfig.getEmitter();
        List<Metadata> metadataList = new ArrayList<>();
        Metadata m1 = new Metadata();
        m1.set("id", "1");
        m1.set(Metadata.CONTENT_LENGTH, "314159");
        m1.set(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT, "the quick brown");
        m1.set(TikaCoreProperties.TITLE, "this is the first title");
        m1.add(TikaCoreProperties.CREATOR, "firstAuthor");
        m1.add(TikaCoreProperties.CREATOR, "secondAuthor");

        Metadata m2 = new Metadata();
        m2.set(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_PATH, "/path_to_this.txt");
        m2.set(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT, "fox jumped over the lazy");
        MetadataFilter filter = tikaConfig.getMetadataFilter();
        filter.filter(m1);
        filter.filter(m2);
        metadataList.add(m1);
        metadataList.add(m2);

        emitter.emit("solr1", metadataList);
    }
}
