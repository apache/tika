package org.apache.tika.server.classic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.resource.TikaMimeTypes;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TikaMimeTypesTest extends CXFTestBase {
    private static final Gson GSON = new GsonBuilder().create();

    private static final String MIMETYPES_PATH = "/mime-types";


    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaMimeTypes.class);
        sf.setResourceProvider(
                TikaMimeTypes.class,
                new SingletonResourceProvider(new TikaMimeTypes())
        );
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetJSON() throws Exception {
        Response response = WebClient
                .create(endPoint + MIMETYPES_PATH)
                .type(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                .accept(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                .get();

        String jsonStr = getStringFromInputStream((InputStream) response.getEntity());
        Map<String, Map<String, Object>> json = (Map<String, Map<String, Object>>)
                GSON.fromJson(jsonStr, Map.class);

        assertEquals(true, json.containsKey("text/plain"));
        assertEquals(true, json.containsKey("application/xml"));
        assertEquals(true, json.containsKey("video/x-ogm"));
        assertEquals(true, json.containsKey("image/bmp"));

        Map<String, Object> bmp = json.get("image/bmp");
        assertEquals(true, bmp.containsKey("alias"));
        List<Object> aliases = (List) bmp.get("alias");
        assertEquals(2, aliases.size());

        assertEquals("image/x-bmp", aliases.get(0));
        assertEquals("image/x-ms-bmp", aliases.get(1));

        String whichParser = bmp.get("parser").toString();
        assertTrue("Which parser", whichParser.equals("org.apache.tika.parser.ocr.TesseractOCRParser") ||
                whichParser.equals("org.apache.tika.parser.image.ImageParser"));

        Map<String, Object> ogm = json.get("video/x-ogm");
        assertEquals("video/ogg", ogm.get("supertype"));
        assertEquals("org.gagravarr.tika.OggParser", ogm.get("parser"));
    }

}
