package org.apache.tika.parser.prt;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.zip.InflaterOutputStream;

import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A basic text extracting parser for the CADKey PRT (CAD Drawing)
 *  format. It outputs text from note entries.
 */
public class PRTParser extends AbstractParser {
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-prt"));
    public static final String PRT_MIME_TYPE = "application/x-prt";
         
    public Set<MediaType> getSupportedTypes(ParseContext context) {
       return SUPPORTED_TYPES;
    }
    
    private static final int MAX_SANE_TEXT_LENGTH = 0x0800;
    
    /*
     * Text types:
     *   00 00 00 00 f0 [3b]f sz sz TEXT     *view name*
     *   00 00 00 00 f0 3f 00 00 00 00 00 00 00 00 sz sz TEXT  *view name*
     *   (anything)  e0 3f sz sz TEXT    *view name*
     *   3x 33 33 33 33 33 e3 3f 0x 00 00 0x 00 00 0x 0x 1f sz sz TEXT    *note entries* 
     *   
     *  Note - all text is null terminated
     */
      
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, 
          ParseContext context) throws IOException, SAXException, TikaException {
       
       XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
       Last5 l5 = new Last5();
       int read;
       
       // Try to get the creation date, which is YYYYMMDDhhmm
       byte[] header = new byte[30];
       IOUtils.readFully(stream, header);
       byte[] date = new byte[12];
       IOUtils.readFully(stream, date);
       
       String dateStr = new String(date, "ASCII");
       if(dateStr.startsWith("19") || dateStr.startsWith("20")) {
          String formattedDate = dateStr.substring(0, 4) + "-" + dateStr.substring(4,6) +
             "-" + dateStr.substring(6,8) + "T" + dateStr.substring(8,10) + ":" +
             dateStr.substring(10, 12) + ":00";
          metadata.set(Metadata.CREATION_DATE, formattedDate);
          metadata.set(Metadata.DATE, formattedDate);
       }
       metadata.set(Metadata.CONTENT_TYPE, PRT_MIME_TYPE);
       
       
       // Now look for text
       while( (read = stream.read()) > -1) {
          if(read == 0xe0 || read == 0xe3 || read == 0xf0) {
             int nread = stream.read();
             if(nread == 0x3f || nread == 0xbf) {
                // Looks promising, check back for a suitable value
                if(read == 0xe3 && nread == 0x3f) {
                   if(l5.is33()) {
                      // Bingo, note text
                      handleNoteText(stream, xhtml);
                   }
                } else if(l5.is00()) {
                   // Likely view name
                   handleViewName(read, nread, stream, xhtml, l5);
                }
             }
          } else {
             l5.record(read);
          }
       }
    }
    
    private void handleNoteText(InputStream stream, XHTMLContentHandler xhtml) 
    throws IOException, SAXException, TikaException {
       // Ensure we have the right padding text
       int read;
       for(int i=0; i<10; i++) {
          read = stream.read();
          if(read >= 0 && read <= 0x0f) {
             // Promising
          } else {
             // Wrong, false detection
             return;
          }
       }
       read = stream.read();
       if(read != 0x1f) {
          // Wrong, false detection
          return;
       }
       
       int length = LittleEndian.readUShort(stream);
       if(length <= MAX_SANE_TEXT_LENGTH) {
          // Length sanity check passed
          handleText(length, stream, xhtml);
       }
    }
    
    private void handleViewName(int typeA, int typeB, InputStream stream, 
          XHTMLContentHandler xhtml, Last5 l5) 
    throws IOException, SAXException, TikaException {
       // Is it 8 byte zero padded?
       int maybeLength = LittleEndian.readUShort(stream);
       if(maybeLength == 0) {
          // Check the next 6 bytes too
          for(int i=0; i<6; i++) {
             int read = stream.read();
             if(read >= 0 && read <= 0x0f) {
                // Promising
             } else {
                // Wrong, false detection
                return;
             }
          }
          
          byte[] b2 = new byte[2];
          IOUtils.readFully(stream, b2);
          int length = LittleEndian.getUShort(b2);
          if(length > 1 && length <= MAX_SANE_TEXT_LENGTH) {
             // Length sanity check passed
             handleText(length, stream, xhtml);
          } else {
             // Was probably something else
             l5.record(b2[0]);
             l5.record(b2[1]);
          }
       } else if(maybeLength > 0 && maybeLength < MAX_SANE_TEXT_LENGTH) {
          // Looks like it's straight into the text
          handleText(maybeLength, stream, xhtml);
       }
    }
    
    private void handleText(int length, InputStream stream, XHTMLContentHandler xhtml) 
    throws IOException, SAXException, TikaException {
       byte[] str = new byte[length];
       IOUtils.readFully(stream, str);
       if(str[length-1] != 0) {
          // Not properly null terminated, must be wrong
          return;
       }
       
       // TODO Is this the right character set?
       String text = new String(str, 0, length-1, "UTF-8");
       
       xhtml.startElement("p");
       xhtml.characters(text);
       xhtml.endElement("p");
    }
    
    /**
     * Provides a view on the previous 5 bytes
     */
    private static class Last5 {
       byte[] data = new byte[5];
       int pos = 0;
       
       private void record(int b) {
          data[pos] = (byte)b;
          pos++;
          if(pos >= data.length) {
             pos = 0;
          }
       }
       
       private byte[] get() {
          byte[] ret = new byte[5];
          for(int i=0; i<ret.length; i++) {
             int p = pos - i;
             if(p < 0) { p += ret.length; }
             ret[i] = data[p];
          }
          return ret;
       }
       
       private boolean is33() {
          byte[] last5 = get();
          for(byte b : last5) {
             if(b != 0x33) return false;
          }
          return true;
       }
       
       private boolean is00() {
          byte[] last5 = get();
          for(byte b : last5) {
             if(b != 0x00) return false;
          }
          return true;
       }
    }
}
