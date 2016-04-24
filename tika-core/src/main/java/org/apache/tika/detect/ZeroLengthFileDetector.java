package org.apache.tika.parser.zerolength;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class ZeroLengthFileDetector implements Detector
{
	public MediaType detect(InputStream stream,Metadata metadata) throws IOException
	{
		   MediaType type = MediaType.OCTET_STREAM;
		 if(stream == null)
			 return type;
		 else
		 {
			 if(stream.available()==0)
			 {
				  type=MediaType.ZeroSize;
				 return type;
			 }
			 else
			 {
				 
				 return type;
			 }
		 }
		
		
	}
}
