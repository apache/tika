package org.apache.tika.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.resource.RecursiveMetadataResource;
import org.apache.tika.server.resource.TikaResource;
import org.apache.tika.server.resource.TikaServerStatus;
import org.apache.tika.server.writer.JSONMessageBodyWriter;
import org.apache.tika.server.writer.JSONObjWriter;
import org.apache.tika.server.writer.MetadataListMessageBodyWriter;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TikaServerStatusTest extends CXFTestBase {

    private final static String STATUS_PATH = "/status";
    private final static String SERVER_ID = UUID.randomUUID().toString();
    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaServerStatus.class);
        sf.setResourceProvider(TikaServerStatus.class,
                new SingletonResourceProvider(new TikaServerStatus(new ServerStatus(SERVER_ID, 0))));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new JSONObjWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testBasic() throws Exception {
        Response response = WebClient.create(endPoint + STATUS_PATH).get();
        String jsonString =
                getStringFromInputStream((InputStream) response.getEntity());
        JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
        assertTrue(root.has("server_id"));
        assertTrue(root.has("status"));
        assertTrue(root.has("millis_since_last_parse_started"));
        assertTrue(root.has("files_processed"));
        assertEquals("OPERATING", root.getAsJsonPrimitive("status").getAsString());
        assertEquals(0, root.getAsJsonPrimitive("files_processed").getAsInt());
        long millis = root.getAsJsonPrimitive("millis_since_last_parse_started").getAsInt();
        assertTrue(millis > 0 && millis < 360000);
        assertEquals(SERVER_ID, root.getAsJsonPrimitive("server_id").getAsString());
    }
}
