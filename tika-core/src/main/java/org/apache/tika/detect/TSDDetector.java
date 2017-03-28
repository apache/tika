//package it.notartel.cons.verifyfiles.custom;
package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.LookaheadInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

// TSD FILE HEX MAGIC CODE:   30 80 06 0B 2A 86 48 86 F7

public class TSDDetector implements Detector {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3659257718174450902L;
	
	//private static final Logger log = LoggerFactory.getLogger(TSDDetector.class);
	
	@Override
	public MediaType detect(InputStream stream, Metadata metadata) throws IOException {
		
		final String hexTSDMagicCode = "3080060B2A864886F7";
		final Integer LookaheadBytes = 9;
		
		MediaType type = MediaType.OCTET_STREAM;
		
		//log.info("TSDDetector --> detect START!");
		//log.info("TSDDetector --> InputStream in: " + stream);
		//log.info("TSDDetector --> Metadata in: " + metadata);
		
	    try(InputStream lookahead = new LookaheadInputStream(stream, LookaheadBytes)) {
	        
	        //log.info("TSDDetector --> detect lookahead instance fo first " + 
	        						  //LookaheadBytes + " bytes from input stream: " + lookahead);
	        
	        byte[] byteTSDMagicCode = hexStringToByteArray(hexTSDMagicCode);
	        //log.info("TSDDetector --> byteTSDMagicCode size: " + byteTSDMagicCode.length);
	        //log.info("TSDDetector --> byteTSDMagicCode: " + Arrays.toString(byteTSDMagicCode));
	        
	        byte[] byteFileStart = new byte[lookahead.available()];
	        lookahead.read(byteFileStart);
	        //log.info("TSDDetector --> byteFileStart size: " + byteFileStart.length);
	        //log.info("TSDDetector --> byteFileStart: " + Arrays.toString(byteFileStart));
	        
	        //Detect if File Type is TSD
	        if(Arrays.equals(byteTSDMagicCode, byteFileStart)) {
	        	type = MediaType.application("timestamped-data");
	        	//log.info("File is a TSD type!");
	        }
	        
	    } catch (Exception ex) {
	    	//log.error("TSDDetector --> detect Error: " + ex.getMessage());
	    	type = MediaType.OCTET_STREAM;
	    } 
	    
        //log.info("TSDDetector --> detected filetype output: " + type);
        //log.info("TSDDetector --> detect END!");
	    
	    return type;
	}
	
	private byte[] hexStringToByteArray(String s) {
	    byte[] b = new byte[s.length() / 2];
	    
	    for (int i = 0; i < b.length; i++) {
	    	int index = i * 2;
	    	int v = Integer.parseInt(s.substring(index, index + 2), 16);
	    	b[i] = (byte) v;
	    }
	    
	    return b;
	}
}