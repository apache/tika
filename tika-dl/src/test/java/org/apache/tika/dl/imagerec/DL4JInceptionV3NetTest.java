package org.apache.tika.dl.imagerec;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;


import static org.junit.Assert.*;

public class DL4JInceptionV3NetTest {

    @Test
    public void recognise() throws Exception {
        TikaConfig config = new TikaConfig(getClass().getResourceAsStream("dl4j-inception3-config.xml"));
        Tika tika = new Tika(config);
        Metadata md = new Metadata();
        tika.parse(getClass().getResourceAsStream("cat.jpg"), md);
        String[] objects = md.getValues("OBJECT");
        boolean found = false;
        for (String object : objects) {
            if (object.contains("_cat")){
                found = true;
            }
        }
        assertTrue(found);
    }
}