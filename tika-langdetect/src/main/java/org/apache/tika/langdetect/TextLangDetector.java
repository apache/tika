/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.langdetect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by trevorlewis on 3/7/16.
 */
/**
 * Language Detection using MIT Lincoln Lab’s Text.jl library
 * https://github.com/trevorlewis/TEXT-Language-REST
 *
 * Please run the Julia lidHttpServer.jl before using this.
 */
public class TextLangDetector extends LanguageDetector {

    private Set<String> languages;
    private CharArrayWriter writer;

    private static URL url;
    private static HttpURLConnection con = null;
    private static OutputStreamWriter out = null;
    private static InputStreamReader in = null;

    public TextLangDetector(){
        super();

        writer = new CharArrayWriter();

        try {
            url = new URL("http://127.0.0.1:8000");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        try {
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            int responseCode = con.getResponseCode();
            if (responseCode == 200) {
                languages = new HashSet<String>();
                in = new InputStreamReader(con.getInputStream());
                String json = getStringFromInputStreamReader(in);
                JsonArray jsonArray = new JsonParser().parse(json).getAsJsonArray();
                for (JsonElement jsonElement: jsonArray) {
                    languages.add(jsonElement.toString());
                }
                in.close();
            }

            con.disconnect();
        } catch (ConnectException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public LanguageDetector loadModels() throws IOException {
        return null;
    }

    @Override
    public LanguageDetector loadModels(Set<String> set) throws IOException {
        return null;
    }

    @Override
    public boolean hasModel(String language) {
        return languages.contains(language);
    }

    @Override
    public LanguageDetector setPriors(Map<String, Float> languageProbabilities) throws IOException {
        return null;
    }

    @Override
    public void reset() {
        writer.reset();
    }

    @Override
    public void addText(char[] cbuf, int off, int len) {
        writer.write(cbuf, off, len);
        writer.write(' ');
    }

    @Override
    public List<LanguageResult> detectAll() {
        List<LanguageResult> result = new ArrayList<>();

        result.add(new LanguageResult(detect(writer.toString()), LanguageConfidence.MEDIUM, 0));

        return result;
    }

    private String detect(String content){
        String language = "error";

        try {
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("PUT");
            con.setDoOutput(true);

            out = new OutputStreamWriter(con.getOutputStream());
            out.write(content);
            out.close();

            int responseCode = con.getResponseCode();
            if (responseCode == 200) {
                in = new InputStreamReader(con.getInputStream());
                String json = getStringFromInputStreamReader(in);
                language = new JsonParser().parse(json).getAsJsonObject().get("lang").getAsString();
                in.close();
            }

            con.disconnect();
        } catch (ConnectException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return language;
    }

    // convert InputStreamReader to String
    private String getStringFromInputStreamReader(InputStreamReader in) {
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            br = new BufferedReader(in);
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }
}
