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

package org.apache.tika.parser.tagratio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import org.apache.tika.exception.TikaException;
import org.htmlparser.Parser;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.json.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.xml.sax.SAXException;

class TextToTagRatioUtil {

	private double[] linkTagList;
	public Object outputDirPath;


	@SuppressWarnings("unchecked")
	public double[] getTagRatioOfFile(String html,String filepath, String dirName) {

		html = html.replaceAll("(?s)<!--.*?-->", "");
		html = html.replaceAll("(?s)<script.*?>.*?</script>", "");
		html = html.replaceAll("(?s)<SCRIPT.*?>.*?</SCRIPT>", "");
		html = html.replaceAll("(?s)<style.*?>.*?</style>", "");
	
		try{
			// To get the filename from absolute path
			Path p1 = Paths.get(filepath);
			String filename = p1.getFileName().toString();

			System.out.println("Output File :"+outputDirPath+"/"+filename);

			//Create file in output directory
			FileWriter fw = new FileWriter(new File(outputDirPath+"/"+filename+".json"));
			BufferedWriter bw = new BufferedWriter(fw);

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
					System.out.println("Tag Ratio : "+linkTagList[i]);
					System.out.println(line);
					
					sb.append(line);
					sb.append("\n");
					tagRatio += linkTagList[i];
					count++;
				
				}
			}

			//Create an write JSON
			JSONObject obj = new JSONObject();
			obj.put("fileName", filename);
			obj.put("absoluteFilePath", filepath);
			obj.put("avgTagRatio", (tagRatio/count));
			obj.put("content", sb.toString());
			
			JSONArray array = new JSONArray();
			array.put(metaTagsMap);
			obj.put("meta-tags", array);
			
			bw.write(obj.toJSONString());
			
			br.close();
			bw.close();
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
	 * @param line
	 * @return
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
			System.out.println("\nLine : "+line+"\nTag : "+tag+"\n"+"Text : "+text);
		}
		return (double) text / (double) tag;
	}



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
			System.out.println(lines.toString());
			return lines.toString();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}

}

public class TextToTagRatio {

	private static String inputDirPath; 
	private static String outputDirPath;
	
	public static void main(String[] args) {
		TextToTagRatioUtil ttr = new TextToTagRatioUtil();		
		TikaUtil tikaUtil = new TikaUtil();
		TextToTagRatio.inputDirPath = args[0];
		TextToTagRatio.outputDirPath = args[1];
		ttr.outputDirPath = outputDirPath;

		//Fetch a polar data file, get xhtml from tika and write to output dir
		File root = new File(inputDirPath);
		File[] listDir = root.listFiles();

		for (File d : listDir) {
			if(d.isFile()) {

						//Get File Path
						String fileAbsolutePath = d.getAbsoluteFile().toString();
						try {
							//Get xhtml for file from tika
							String xhtmlOutput = tikaUtil.parseToHTML(fileAbsolutePath);

							//Get Tag Ratio data for File
							String output = ttr.readXhtmlData(xhtmlOutput);

							ttr.getTagRatioOfFile(output,fileAbsolutePath, d.getName());
							
						} catch (IOException | SAXException | TikaException e) {
							System.out.println("Tika Exception occurred for file: "+ fileAbsolutePath);
						}

					}
			
		}


	}


}

