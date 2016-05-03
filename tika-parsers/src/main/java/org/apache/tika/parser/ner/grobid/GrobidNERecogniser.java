import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.tika.parser.ner.NERecogniser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Created by minhpham on 5/1/16.
 */
public class GrobidNERecogniser implements NERecogniser {

    public static final Set<String> ENTITY_TYPES = new HashSet<String>() {{
        add("MEASUREMENT_NUMBERS");
        add("MEASUREMENT_UNITS");
        add("MEASUREMENTS");
        add("NORMALIZED_MEASUREMENTS");
    }};

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<String> getEntityTypes() {
        return ENTITY_TYPES;
    }

    @Override
    public Map<String, Set<String>> recognise(String s) {

        if (s.isEmpty()) {
            return new HashMap<>();
        }

        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://localhost:8080/processQuantityText");

        List<NameValuePair> params = new ArrayList<>(1);
        params.add(new BasicNameValuePair("text", s));
        try {
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        HttpResponse response = null;
        try {
            response = httpclient.execute(httppost);
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
        String jsonStr = null;
        try {
            jsonStr = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
        }

//        System.out.println(jsonStr);

        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            JSONArray msJsonArray = jsonObject.getJSONArray("measurements");

            HashMap<String, Set<String>> resultMap = new HashMap<>();
            for (String entityType : ENTITY_TYPES) {
                resultMap.put(entityType, new HashSet<>());
            }

            for (int i = 0; i < msJsonArray.length(); i++) {
                JSONObject obj = msJsonArray.getJSONObject(i);

                JSONObject quantityObject = obj.getJSONObject("quantity");


                if (quantityObject != null) {

                    if (quantityObject.has("rawValue")) {
                        String value = quantityObject.getString("rawValue");

                        resultMap.get("MEASUREMENT_NUMBERS").add(value);
                        resultMap.get("MEASUREMENTS").add(value);
                    }
                    if (quantityObject.has("normalizedQuantity")) {
                        String value = quantityObject.getString("normalizedQuantity");

                        resultMap.get("NORMALIZED_MEASUREMENTS").add(value);
                        resultMap.get("MEASUREMENTS").add(value);
                    }
                    if (quantityObject.has("rawUnit")) {
                        String value = quantityObject.getJSONObject("rawUnit").getString("name");

                        resultMap.get("MEASUREMENT_UNITS").add(value);
                        resultMap.get("MEASUREMENTS").add(value);
                    }
                    if (quantityObject.has("normalizedUnit")) {
                        String value = quantityObject.getJSONObject("normalizedUnit").getString("name");

                        resultMap.get("NORMALIZED_MEASUREMENTS").add(value);
                        resultMap.get("MEASUREMENTS").add(value);
                    }
                }
            }
            return resultMap;
        } catch (Exception e) {
//            e.printStackTrace();
            return new HashMap<>();
        }

    }
}
