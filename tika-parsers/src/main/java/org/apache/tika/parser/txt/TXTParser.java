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
package org.apache.tika.parser.txt;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.htmlparser.Parser;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.json.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Utility class for implementing TagRatio Parser. It has methods to compute the tag ratio
 * and to read the xhtml document to do the computation.
 *
 * @since Apache Tika 1.13
 */

class TextToTagRatioUtil {

	private double[] linkTagList;


	/**
	 * Calculate Text to Tag Ratio for raw html string
	 * @param Raw html passed as string
	 * @return list of tag ratios
	 */
	
	@SuppressWarnings("unchecked")
	public double[] getTagRatioOfHtml(String html) {

		html = html.replaceAll("(?s)<!--.*?-->", "");
		html = html.replaceAll("(?s)<script.*?>.*?</script>", "");
		html = html.replaceAll("(?s)<SCRIPT.*?>.*?</SCRIPT>", "");
		html = html.replaceAll("(?s)<style.*?>.*?</style>", "");
	
		try{
			Parser p = new Parser(html);
			NodeList nl = p.parse(null);
			
			BufferedReader br = new BufferedReader(new StringReader(nl.toHtml()));
			int numLines = 0;
			while (br.readLine() != null) {
				numLines++;
			}
			br.close();

			linkTagList = new double[numLines];
			HashMap<String, String> metaTagsMap = new HashMap<String, String>();
			String line;
			double threshold = 10;
			StringBuffer sb = new StringBuffer();
			double tagRatio = 0.0;
			int count = 0;
			br = new BufferedReader(new StringReader(nl.toHtml()));
			for (int i = 0; i < linkTagList.length; i++) {
				line = br.readLine();
				line = line.trim();
				if (line.equals("")) {
					continue;
				}
				linkTagList[i] = computeTextToTagRatio(line);
				//Extract meta tags
				populateMetaTags(line, metaTagsMap);
				
				if(linkTagList[i] != 0 && linkTagList[i] >= threshold){
					
					sb.append(line);
					sb.append("\n");
					tagRatio += linkTagList[i];
					count++;
				}
			}

			//Create a JSON Object
			JSONObject obj = new JSONObject();
			obj.put("avgTagRatio", (tagRatio/count));
			obj.put("content", sb.toString());
			
			JSONArray array = new JSONArray();
			array.put(metaTagsMap);
			obj.put("meta-tags", array);
			
			
			br.close();
			
		}catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserException e) {
			e.printStackTrace();
			return linkTagList;
		}
		return linkTagList;
	}

	/**
	 * Populate the metadata information
	 * @param line
	 * @param metaTagsMap Hash Map to store metadata as key value pairs
	 * @return
	 */

	private void populateMetaTags(String line, HashMap<String, String> metaTagsMap) {
		
		if(line.startsWith("<meta ")){
				
			Document doc = Jsoup.parse(line);
			Element tag = doc.select("meta").first();
			String name = tag.attr("name");
			String content = tag.attr("content");
 			
			metaTagsMap.put(name, content);
		}
	}

	/**
	 * Calculate Text to Tag Ratio for line
	 * @param line Line in the raw html
	 * @return Tag Ratio of the line
	 */
	private double computeTextToTagRatio(String line) {
		int tag = 0;
		int text = 0;

		for (int i = 0; i >= 0 && i < line.length(); i++) {
			if (line.charAt(i) == '<') {	//start tag
				tag++;
				i = line.indexOf('>', i);
				if (i == -1) {
					break;
				}
			} else if (tag == 0 && line.charAt(i) == '>') {	//close tag
				text = 0;
				tag++;
			} else {		//just text
				text++;
			}

		}
		if (tag == 0) {
			tag = 1;
		}
		if(text != 0){
		}
		return (double) text / (double) tag;
	}


	/**
	 * Read Xhtml data from output of Tika's AutoDetect Parser
	 * @param xhtmlOutput Xhtml output from Tika passed as input to Tag Ratio Parser
	 * @return html string
	 */

	public String readXhtmlData(String xhtmlOutput) {
		
		// convert String into InputStream
		InputStream is = new ByteArrayInputStream(xhtmlOutput.getBytes());

		// read it with BufferedReader
		BufferedReader br = new BufferedReader(new InputStreamReader(is));

		String line = null;
		StringBuffer lines = new StringBuffer();
		try {
			while ((line = br.readLine()) != null) {
				lines.append(line);
				lines.append("\n");
			}
			return lines.toString();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}

}


/**
 * Plain text parser. The text encoding of the document stream is
 * automatically detected based on the byte patterns found at the
 * beginning of the stream and the given document metadata, most
 * notably the <code>charset</code> parameter of a
 * {@link org.apache.tika.metadata.HttpHeaders#CONTENT_TYPE} value.
 * <p/>
 * This parser sets the following output metadata entries:
 * <dl>
 * <dt>{@link org.apache.tika.metadata.HttpHeaders#CONTENT_TYPE}</dt>
 * <dd><code>text/plain; charset=...</code></dd>
 * </dl>
 */

public class TXTParser extends AbstractParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -6656102320836888910L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.TEXT_PLAIN);

    private static final ServiceLoader LOADER =
            new ServiceLoader(TXTParser.class.getClassLoader());

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // Automatically detect the character encoding
        try (AutoDetectReader reader = new AutoDetectReader(
                new CloseShieldInputStream(stream), metadata,
                context.get(ServiceLoader.class, LOADER))) {
            Charset charset = reader.getCharset();
            MediaType type = new MediaType(MediaType.TEXT_PLAIN, charset);
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            // deprecated, see TIKA-431
            metadata.set(Metadata.CONTENT_ENCODING, charset.name());

            XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();

            xhtml.startElement("p");
            char[] buffer = new char[4096];
            int n = reader.read(buffer);
            while (n != -1) {
                xhtml.characters(buffer, 0, n);
                n = reader.read(buffer);
            }
            xhtml.endElement("p");

            xhtml.endDocument();
        }
    }
    
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context, boolean flag)
            throws IOException, SAXException, TikaException {
    	
    	// Create an object of the utility class
    	TextToTagRatioUtil ttr = new TextToTagRatioUtil();
    	
        // Automatically detect the character encoding
        try (AutoDetectReader reader = new AutoDetectReader(
                new CloseShieldInputStream(stream), metadata,
                context.get(ServiceLoader.class, LOADER))) {
            Charset charset = reader.getCharset();
            MediaType type = new MediaType(MediaType.TEXT_PLAIN, charset);
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            // deprecated, see TIKA-431
            metadata.set(Metadata.CONTENT_ENCODING, charset.name());

            XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();

            xhtml.startElement("p");
            char[] buffer = new char[4096];
            int n = reader.read(buffer);
            while (n != -1) {
                xhtml.characters(buffer, 0, n);
                n = reader.read(buffer);
            }
            xhtml.endElement("p");

            xhtml.endDocument();
            		
		
            try {
            	
            	if(flag == true){
            		
        		String xhtmlOutput = xhtml.toString();
				
				//Get Tag Ratio data for xhtml
				String output = ttr.readXhtmlData(xhtmlOutput);

				ttr.getTagRatioOfHtml(output);
            	}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
    }

}
