package org.apache.tika.client;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpClientUtil {

    private static HttpClient CLIENT = HttpClients.createDefault();

    public static boolean postJson(String url, String json) throws IOException,
            TikaClientException {
        HttpPost post = new HttpPost(url);
        ByteArrayEntity entity = new ByteArrayEntity(json.getBytes(StandardCharsets.UTF_8));
        post.setEntity(entity);
        post.setHeader("Content-Type", "application/json");
        HttpResponse response = CLIENT.execute(post);


        if (response.getStatusLine().getStatusCode() != 200) {
            String msg = EntityUtils.toString(response.getEntity());
            throw new TikaClientException("Bad status: " +
                    response.getStatusLine().getStatusCode() + " : "+
                    msg);
        } else {
            String msg = EntityUtils.toString(response.getEntity());
            System.out.println("httputil: " + msg);
        }
        return true;
    }
}
