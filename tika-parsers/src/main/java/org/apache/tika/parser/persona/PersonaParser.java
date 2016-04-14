package org.apache.tika.parser.persona;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;

import edu.usc.ir.UserExtractor;

public class PersonaParser extends AbstractParser {

	
	
	public static final Set<MediaType> MEDIA_TYPES = new HashSet<>();
	
	static {
        MEDIA_TYPES.add(MediaType.TEXT_HTML);
        MEDIA_TYPES.add(MediaType.TEXT_PLAIN);
    }
	@Override
	public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
		// TODO Auto-generated method stub
		return MEDIA_TYPES;
	}

	@Override
	public void parse(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, ParseContext parseContext)
			throws IOException, SAXException, TikaException {
		// TODO Auto-generated method stub
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		ArrayList<String> data = new ArrayList<String>();
		String line = reader.readLine();
		while(line != null){
           
            data.add(line);
            line = reader.readLine();
            
            
        }           
		
	
		String host = data.get(0);
		Properties prop = new Properties();
		InputStream input = PersonaParser.class.getClassLoader().getResourceAsStream("org/apache/tika/parser/persona/personaparser.properties");
		prop.load(input);
		
		String user = prop.getProperty("user");
		String password = prop.getProperty("password");
		String start = data.get(1);
		String rows = data.get(2);
		StringBuilder builder = new StringBuilder();
		UserExtractor userdata = new UserExtractor();
		try {
			HashMap<String, ArrayList<String>> Map = userdata.persons(host,user,password,Integer.parseInt(start),Integer.parseInt(rows));
			metadata.set(Metadata.CONTENT_TYPE, "application/text");
			metadata.set("DocumentCount",Integer.toString(Map.size()));
			for (Entry<String, ArrayList<String>> entry : Map.entrySet()) {
				
		      
		        builder.append(entry.getKey());
		        builder.append(":");
		        builder.append(entry.getValue());
		    
		        builder.append("\n");
		    }
		} catch (FailingHttpStatusCodeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	
	XHTMLContentHandler xhtml = new XHTMLContentHandler(contentHandler, metadata);
	xhtml.startDocument();
	xhtml.characters(builder.toString());
    xhtml.endDocument();
	}

}
