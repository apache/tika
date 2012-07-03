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
package org.apache.tika.parser.iptc;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for IPTC ANPA New Wire Feeds
 */
public class IptcAnpaParser implements Parser {
    /** Serial version UID */
    private static final long serialVersionUID = -6062820170212879115L;

    private static final MediaType TYPE =
        MediaType.text("vnd.iptc.anpa");

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(TYPE);

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
           InputStream stream, ContentHandler handler,
           Metadata metadata, ParseContext context)
           throws IOException, SAXException, TikaException {

        HashMap<String,String> properties = this.loadProperties(stream);
        this.setMetadata(metadata, properties);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        // TODO: put body content here
        xhtml.startElement("p");
        String body = clean(properties.get("body"));
        if (body != null)
           xhtml.characters(body);
        xhtml.endElement("p");
        xhtml.endDocument();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }


   private int FMT_ANPA_1312    = 0x00;   // "NAA 89-3 (ANPA 1312)"
   private int FMT_ANPA_UPI     = 0x01;   // "United Press International ANPA 1312 variant"
   private int FMT_ANPA_UPI_DL  = 0x02;   // "United Press International Down-Load Message"
   private int FMT_IPTC_7901    = 0x03;   // "IPTC7901 Recommended Message Format"
   private int FMT_IPTC_PHOTO   = 0x04;   // "IPTC-NAA Digital Newsphoto Parameter Record"
   private int FMT_IPTC_CHAR    = 0x05;   // "IPTC Unstructured Character Oriented File Format (UCOFF)"
   private int FMT_NITF         = 0x06;   // "News Industry Text Format (NITF)"
   private int FMT_NITF_TT      = 0x07;   // "Tidningarnas Telegrambyra NITF version (TTNITF DTD)"
   private int FMT_NITF_RB      = 0x08;   // "Ritzaus Bureau NITF version (RBNITF DTD)"
   private int FMT_IPTC_AP      = 0x09;   // "Associated Press news wire format"
   private int FMT_IPTC_BLM     = 0x0A;   // "Bloomberg News news wire format"
   private int FMT_IPTC_NYT     = 0x0B;   // "New York Times news wire format"
   private int FMT_IPTC_RTR     = 0x0C;   // "Reuters news wire format"

   private int FORMAT = FMT_ANPA_1312;    // assume the default format to be ANPA-1312

   private final static char SOH = 0x01;    // start of header (ctrl-a)
   private final static char STX = 0x02;    // start of text (ctrl-b)
   private final static char ETX = 0x03;    // end of text (ctrl-c)
   private final static char EOT = 0x04;    // the tab character (ctrl-d)
   private final static char SYN = 0x16;    // synchronous idle (ctrl-v)

   private final static char BS = 0x08;    // the backspace character (used for diacriticals)
   private final static char TB = 0x09;    // the tab character
   private final static char LF = 0x0A;    // line feed
   private final static char FF = 0x0C;    // form feed
   private final static char CR = 0x0D;    // carriage return
   private final static char XQ = 0x11;    // device control (ctrl-q)
   private final static char XS = 0x13;    // device control (ctrl-s)
   private final static char FS = 0x1F;    // a field delimiter

   private final static char HY = 0x2D;    // hyphen
   private final static char SP = 0x20;    // the blank space
   private final static char LT = 0x3C;    // less than
   private final static char EQ = 0x3D;    // less than
   private final static char CT = 0x5E;    // carat

   private final static char SL = 0x91;    // single-quote left
   private final static char SR = 0x92;    // single-quote right
   private final static char DL = 0x93;    // double-quote left
   private final static char DR = 0x94;    // double-quote right


   /**
    * scan the news messsage and store the metadata and data into a map
    */
   private HashMap<String,String> loadProperties(InputStream is) {
      
      HashMap<String,String> properties = new HashMap<String,String>();

      FORMAT = this.scanFormat(is);

      byte[] residual = this.getSection(is,"residual");

      byte[] header = this.getSection(is,"header");
      parseHeader(header, properties);

      byte[] body = this.getSection(is,"body");
      parseBody(body, properties);

      byte[] footer = this.getSection(is,"footer");
      parseFooter(footer, properties);
       
      return (properties);
   }


   private int scanFormat(InputStream is) {
      int format    = this.FORMAT;
      int  maxsize  = 524288;     //  512K

      byte[] buf = new byte[maxsize];
      try {
         if (is.markSupported()) {
            is.mark(maxsize);
         }
         int msgsize = is.read(buf);                // read in at least the full data

         String message = (new String(buf)).toLowerCase();
         // these are not if-then-else, because we want to go from most common
         // and fall through to least.  this is imperfect, as these tags could
         // show up in other agency stories, but i can't find a spec or any
         // explicit codes to identify the wire source in the message itself

         if (message.contains("ap-wf")) {
            format = this.FMT_IPTC_AP;
         }
         if (message.contains("reuters")) {
            format = this.FMT_IPTC_RTR;
         }
         if (message.contains("new york times")) {
            format = this.FMT_IPTC_NYT;
         }
         if (message.contains("bloomberg news")) {
            format = this.FMT_IPTC_BLM;
         }
      }
      catch (IOException eio) {
         // we are in an unstable state
      }

      try {
         if (is.markSupported()) {
            is.reset();
         }
      }
      catch (IOException eio) {
         // we are in an unstable state
      }
      return (format);
   }


   private void setFormat(int format) {
      this.FORMAT = format;
   }


   private String getFormatName() {
      
      String name = "";
      
      if (FORMAT == this.FMT_IPTC_AP) {
         name = "Associated Press";
      }
      
      else if(FORMAT == this.FMT_IPTC_BLM) {
         name = "Bloomberg";
      }

      else if(FORMAT == this.FMT_IPTC_NYT) {
         name = "New York Times";
      }

      else if(FORMAT == this.FMT_IPTC_RTR) {
         name = "Reuters";
      }

      return (name);
   }


   private byte[] getSection(InputStream is, String name) {

      byte[] value = new byte[0];

      if (name.equals("residual")) {
         // the header shouldn't be more than 1k, but just being generous here
         int  maxsize  = 8192;     //  8K
         byte bstart   = SYN;     // check for SYN [0x16 : ctrl-v] (may have leftover residue from preceding message)
         byte bfinish  = SOH;     // check for SOH [0x01 : ctrl-a] (typically follows a pair of SYN [0x16 : ctrl-v])
         value = getSection(is, maxsize, bstart, bfinish, true);
      }

      else if(name.equals("header")) {
         // the header shouldn't be more than 1k, but just being generous here
         int  maxsize  = 8192;     //  8K
         byte bstart   = SOH;     // check for SOH [0x01 : ctrl-a] (typically follows a pair of SYN [0x16 : ctrl-v])
         byte bfinish  = STX;     // check for STX [0x02 : ctrl-b] (marks end of header, beginning of message)
         value = getSection(is, maxsize, bstart, bfinish, true);
      }

      else if (name.equals("body")) {
         // the message shouldn't be more than 16k (?), leaving plenty of space
         int  maxsize  = 524288;     //  512K
         byte bstart   = STX;     // check for STX [0x02 : ctrl-b] (marks end of header, beginning of message)
         byte bfinish  = ETX;     // check for ETX [0x03 : ctrl-c] (marks end of message, beginning of footer)
         value = getSection(is, maxsize, bstart, bfinish, true);
      }

      else if (name.equals("footer")) {
         // the footer shouldn't be more than 1k , leaving plenty of space
         int maxsize   = 8192;     //  8K
         byte bstart   = ETX;     // check for ETX [0x03 : ctrl-c] (marks end of message, beginning of footer)
         byte bfinish  = EOT;     // check for EOT [0x04 : ctrl-d] (marks end of transmission)
         value = getSection(is, maxsize, bstart, bfinish, true);
      }

      return (value);
   }


   private byte[] getSection(InputStream is, int maxsize, byte bstart, byte bfinish, boolean ifincomplete) {
      byte[] value  = new byte[0];

      try {
         boolean started = false;                   // check if we have found the start flag
         boolean finished = false;                  // check if we have found the finish flag
         int read = 0;                              // the number of bytes we read
         int start = 0;                             // the position after the start flag

         // TODO: this only pulls back 8K of data on a read, regardless of buffer size
         //       more nefariously, it caps at a total 8K, through all sections
         int streammax = is.available();
         maxsize = Math.min(maxsize, streammax);

         is.mark(maxsize);
         byte[] buf = new byte[maxsize];
         int totsize = 0;
         int remainder = maxsize - totsize;
         while (remainder > 0) {
            int msgsize = is.read(buf, maxsize-remainder, maxsize);    // read in at least the full data
            if (msgsize == -1) {
               remainder = msgsize = 0;
            }
            remainder -= msgsize;
            totsize   += msgsize;
         }

         // scan through the provided input stream
         for (read=0; read < totsize; read++) {
            byte b = buf[read];

            if (!started) {
               started = (b == bstart);
               start = read + 1;
               continue;
            }

            if (finished = (b == bfinish)) {
/*
               is.reset();
               long skipped = is.skip((long)read);
               if (skipped != read) {
                  // we are in an unstable state
               }
               is.mark(1);
 */
               break;
            }

            // load from the stream until we run out of characters, or hit the termination byte
            continue;
         }

         // move the input stream back to where it was initially
         is.reset();

         if (finished) {
            // now, we want to reset the stream to be sitting right on top of the finish marker
            is.skip(read);
            value = new byte[read-start];
            System.arraycopy(buf, start, value, 0, read-start);
         }
         else {
            if (ifincomplete && started) {
               // the caller wants anything that was read, and we finished the stream or buffer
               value = new byte[read-start];
               System.arraycopy(buf, start, value, 0, read-start);
            }
         }
      }
      catch (IOException eio) {
         // something invalid occurred, return an empty string
      }

      return (value);
   }


   private boolean parseHeader(byte[] value, HashMap<String,String> properties) {
      boolean added = false;

      String env_serviceid = "";
      String env_category = "";
      String env_urgency = "";
      String hdr_edcode = "";
      String hdr_subject = "";
      String hdr_date = "";
      String hdr_time = "";

      int read = 0;

      while (read < value.length) {

         // pull apart the envelope, getting the service id  (....\x1f)
         while (read < value.length) {
            byte val_next = value[read++];
            if (val_next != FS) {
               env_serviceid += (char)(val_next & 0xff);  // convert the byte to an unsigned int
            }
            else {
               break;
            }
         }

         // pull apart the envelope, getting the category  (....\x13\x11)
         while (read < value.length) {
            byte val_next = value[read++];
            if (val_next != XS) {   // the end of the envelope is marked (\x13)
               env_category += (char)(val_next & 0xff);  // convert the byte to an unsigned int
            }
            else {
               val_next = value[read];  // get the remaining byte (\x11)
               if (val_next == XQ) {
                  read++;
               }
               break;
            }
         }

         // pull apart the envelope, getting the subject heading
         while (read < value.length) {
            boolean subject = true;
            byte val_next = value[read++];
            while ((subject) && (val_next != SP) && (val_next != 0x00)) {  // ignore the envelope subject
               hdr_subject += (char)(val_next & 0xff);  // convert the byte to an unsigned int
               val_next =  (read < value.length) ? value[read++] : 0x00;
               while (val_next == SP) {  // consume all the spaces
                  subject = false;
                  val_next =  (read < value.length) ? value[read++] : 0x00;
                  if (val_next != SP) {
                     --read;  // otherwise we eat into the next section
                  }
               }
            }
            if (!subject) {
               break;
            }
         }

         // pull apart the envelope, getting the date and time
         while (read < value.length) {
            byte val_next = value[read++];
            if (hdr_date.length() == 0) {
               while (((val_next >= (byte)0x30) && (val_next <= (byte)0x39))  // consume all numerics and hyphens
                  ||   (val_next == HY)) {
                  hdr_date += (char)(val_next & 0xff);  // convert the byte to an unsigned int
                  val_next =  (read < value.length) ? value[read++] : 0x00;
               }
            }
            else if (val_next == SP) {
               while (val_next == SP) {  // consume all the spaces
                  val_next =  (read < value.length) ? value[read++] : 0x00;
               }
               continue;
            }
            else {
               while (((val_next >= (byte)0x30) && (val_next <= (byte)0x39))  // consume all numerics and hyphens
                  ||   (val_next == HY)) {
                  hdr_time += (char)(val_next & 0xff);  // convert the byte to an unsigned int
                  val_next =  (read < value.length) ? value[read++] : 0x00;
               }
            }
         }
         break; // don't let this run back through and start thrashing metadata
      }

      // if we were saving any of these values, we would set the properties map here

      added = (env_serviceid.length() + env_category.length() + hdr_subject.length() + 
               hdr_date.length() + hdr_time.length()) > 0; 
      return added;
   }

   private boolean parseBody(byte[] value, HashMap<String,String> properties) {
      boolean added = false;

      String bdy_heading = "";
      String bdy_title = "";
      String bdy_source = "";
      String bdy_author = "";
      String bdy_body = "";

      int read = 0;
      boolean done = false;

      while (!done && (read < value.length)) {

         // pull apart the body, getting the heading (^....\x0d\x0a)
         while (read < value.length) {
            byte val_next = value[read++];
            if (val_next == CT) {      //  start of a new section , first is the heading
               val_next =  (read < value.length) ? value[read++] : 0x00;
               // AP, NYT, and Bloomberg end with < , Reuters with EOL
               while ((val_next != LT) && (val_next != CR) && (val_next != LF)) {   // less than delimiter (\x3c) and not EOL
                  bdy_heading += (char)(val_next & 0xff);  // convert the byte to an unsigned int
                  val_next =  (read < value.length) ? value[read++] : 0x00;
                  if (read > value.length) { break; }  // shouldn't ever hit this, but save a NPE
               }
               if (val_next == LT) {
                  // hit the delimiter, carry on
                  val_next =  (read < value.length) ? value[read++] : 0x00;
               }
               while (bdy_heading.length() > 0 && ((val_next == CR) || (val_next == LF))) {
                  val_next =  (read < value.length) ? value[read++] : 0x00;  // skip the new lines
                  if ((val_next != CR) && (val_next != LF)) {
                     --read;
                  }
               }
            }
            else {
               // this will only be hit on poorly-formed files

               // for reuters, the heading does not start with the ^, so we push one back into the stream
               if (FORMAT == this.FMT_IPTC_RTR) {
                  if (val_next != CT) {
                     // for any non-whitespace, we need to go back an additional step to non destroy the data
                     if ((val_next != SP) && (val_next != TB) && (val_next != CR) && (val_next != LF)) {
                        // if the very first byte is data, we have to shift the whole array, and stuff in a carat
                        if (read == 1) {
                           byte[] resize = new byte[value.length + 1];
                           System.arraycopy(value, 0, resize, 1, value.length);
                           value = resize;
                        }
                     }
                     value[--read] = CT;
                     continue;
                  }
               }
            }
            break;
         }

         // pull apart the body, getting the title (^....\x0d\x0a)
         while (read < value.length) {
            byte val_next = value[read++];
            if (val_next == CT) {      //  start of a new section , first is the heading
               val_next =  (read < value.length) ? value[read++] : 0x00;
               // AP, NYT, and Bloomberg end with < , Reuters with EOL
               while ((val_next != LT) && (val_next != CT) && (val_next != CR) && (val_next != LF)) {   // less than delimiter (\x3c), or carat (\x5e) and not EOL
                  bdy_title += (char)(val_next & 0xff);  // convert the byte to an unsigned int
                  val_next =  (read < value.length) ? value[read++] : 0x00;
                  if (read > value.length) { break; }  // shouldn't ever hit this, but save a NPE
               }

               if (val_next == CT) {      //  start of a new section , when first didn't finish cleanly
                   --read;
               }

               if (val_next == LT) {
                  // hit the delimiter, carry on
                  val_next =  (read < value.length) ? value[read++] : 0x00;
               }

               while (bdy_title.length() > 0 && ((val_next == CR) || (val_next == LF))) {
                  val_next =  (read < value.length) ? value[read++] : 0x00;  // skip the new lines
                  if ((val_next != CR) && (val_next != LF)) {
                     --read;
                  }
               }
            }
            else {
               // this will only be hit on poorly-formed files

               // for bloomberg, the title does not start with the ^, so we push one back into the stream
               if (FORMAT == this.FMT_IPTC_BLM) {
                  if (val_next == TB) {
                     value[--read] = CT;
                     continue;
                  }
               }

               // for reuters, the title does not start with the ^, so we push one back into the stream
               if (FORMAT == this.FMT_IPTC_RTR) {
                  if (val_next != CT) {
                     // for any non-whitespace, we need to go back an additional step to non destroy the data
                     if ((val_next != SP) && (val_next != TB) && (val_next != CR) && (val_next != LF)) {
                        --read;
                     }
                     value[--read] = CT;
                     continue;
                  }
               }
            }
            break;
         }


         // at this point, we have a variable number of metadata lines, with various orders
         // we scan the start of each line for the special character, and run to the end character
         // pull apart the body, getting the title (^....\x0d\x0a)
         boolean metastarted = false;
         String longline = "";
         String longkey = "";
         while (read < value.length) {
            byte val_next = value[read++];

            // eat up whitespace before committing to the next section
            if ((val_next == SP) || (val_next == TB) || (val_next == CR) || (val_next == LF)) {
               continue;
            }

            if (val_next == CT) {      //  start of a new section , could be authors, sources, etc
               val_next =  (read < value.length) ? value[read++] : 0x00;
               String tmp_line = "";
               while ((val_next != LT) && (val_next != CT) && (val_next != CR) && (val_next != LF) && (val_next != 0))  {
                  // less than delimiter (\x3c), maybe also badly formed with just new line
                  tmp_line += (char)(val_next & 0xff);  // convert the byte to an unsigned int
                  val_next =  (read < value.length) ? value[read++] : 0x00;
                  if (read > value.length) { break; }  // shouldn't ever hit this, but save a NPE
               }

               if (val_next == CT) {      //  start of a new section , when first didn't finish cleanly
                   --read;
               }

               if (val_next == LT) {
                  // hit the delimiter, carry on
                  val_next =  (read < value.length) ? value[read++] : 0x00;
               }

               while ((val_next == CR) || (val_next == LF)) {
                  val_next =  (read < value.length) ? value[read++] : 0x00;  // skip the new lines
                  if ((val_next != CR) && (val_next != LF)) {
                     --read;
                  }
               }
               if (tmp_line.toLowerCase().startsWith("by") || longline.equals("bdy_author")) {
                  longkey = "bdy_author";

                  // prepend a space to subsequent line, so it gets parsed consistent with the lead line
                  tmp_line = (longline.equals(longkey) ? " " : "") + tmp_line;

                  // we have an author candidate
                  int term = tmp_line.length();
                  term = Math.min(term, (tmp_line.indexOf("<")  > -1 ? tmp_line.indexOf("<")  : term));
                  term = Math.min(term, (tmp_line.indexOf("=")  > -1 ? tmp_line.indexOf("=")  : term));
                  term = Math.min(term, (tmp_line.indexOf("\n") > -1 ? tmp_line.indexOf("\n") : term));
                  term = (term > 0 ) ? term : tmp_line.length();
                  bdy_author += tmp_line.substring(tmp_line.indexOf(" "), term);
                  metastarted = true;
                  longline = ((tmp_line.indexOf("=")  > -1) && (!longline.equals(longkey)) ? longkey : "");
               }
               else if (FORMAT == this.FMT_IPTC_BLM) {
                  String byline = "   by ";
                  if (tmp_line.toLowerCase().contains(byline)) {
                     longkey = "bdy_author";

                     int term = tmp_line.length();
                     term = Math.min(term, (tmp_line.indexOf("<")  > -1 ? tmp_line.indexOf("<")  : term));
                     term = Math.min(term, (tmp_line.indexOf("=")  > -1 ? tmp_line.indexOf("=")  : term));
                     term = Math.min(term, (tmp_line.indexOf("\n") > -1 ? tmp_line.indexOf("\n") : term));
                     term = (term > 0 ) ? term : tmp_line.length();
                     // for bloomberg, the author line sits below their copyright statement
                     bdy_author += tmp_line.substring(tmp_line.toLowerCase().indexOf(byline) + byline.length(), term) + " ";
                     metastarted = true;
                     longline = ((tmp_line.indexOf("=")  > -1) && (!longline.equals(longkey)) ? longkey : "");
                  }
                  else if(tmp_line.toLowerCase().startsWith("c.")) {
                     // the author line for bloomberg is a multiline starting with c.2011 Bloomberg News
                     // then containing the author info on the next line
                     if (val_next == TB) {
                        value[--read] = CT;
                        continue;
                     }
                  }
                  else if(tmp_line.toLowerCase().trim().startsWith("(") && tmp_line.toLowerCase().trim().endsWith(")")) {
                     // the author line may have one or more comment lines between the copyright
                     // statement, and the By AUTHORNAME line
                     if (val_next == TB) {
                        value[--read] = CT;
                        continue;
                     }
                  }
               }

               else if (tmp_line.toLowerCase().startsWith("eds") || longline.equals("bdy_source")) {
                  longkey = "bdy_source";
                  // prepend a space to subsequent line, so it gets parsed consistent with the lead line
                  tmp_line = (longline.equals(longkey) ? " " : "") + tmp_line;

                  // we have a source candidate
                  int term = tmp_line.length();
                  term = Math.min(term, (tmp_line.indexOf("<")  > -1 ? tmp_line.indexOf("<")  : term));
                  term = Math.min(term, (tmp_line.indexOf("=")  > -1 ? tmp_line.indexOf("=")  : term));
//                  term = Math.min(term, (tmp_line.indexOf("\n") > -1 ? tmp_line.indexOf("\n") : term));
                  term = (term > 0 ) ? term : tmp_line.length();
                  bdy_source += tmp_line.substring(tmp_line.indexOf(" ") + 1, term) + " ";
                  metastarted = true;
                  longline = (!longline.equals(longkey) ? longkey  : "");
               }
               else {
                  // this has fallen all the way through.  trap it as part of the subject,
                  // rather than just losing it
                  if (!metastarted) {
                     bdy_title += " , " + tmp_line;     //  not sure where else to put this but in the title
                  }
                  else {
                     // what to do with stuff that is metadata, which falls after metadata lines started?
                     bdy_body += " " + tmp_line + " , ";     //  not sure where else to put this but in the title
                  }
               }
            }
            else {  // we're on to the main body
               while ((read < value.length) && (val_next != 0))  {
                  // read until the train runs out of tracks
                  bdy_body += (char)(val_next & 0xff);  // convert the byte to an unsigned int
                  val_next =  (read < value.length) ? value[read++] : 0x00;
                  if (read > value.length) { break; }  // shouldn't ever hit this, but save a NPE
               }

            }
            // we would normally break here, but just let this read out to the end
         }
         done = true; // don't let this run back through and start thrashing metadata
      }
      properties.put("body", bdy_body);
      properties.put("title", bdy_title);
      properties.put("subject", bdy_heading);
      properties.put("author", bdy_author);
      properties.put("source", bdy_source);

      added = (bdy_body.length() + bdy_title.length() + bdy_heading.length() + bdy_author.length() +
               bdy_source.length()) > 0;
      return added;
   }


   private boolean parseFooter(byte[] value, HashMap<String,String> properties) {
      boolean added = false;

      String ftr_source = "";
      String ftr_datetime = "";

      int read = 0;
      boolean done = false;

      while (!done && (read < value.length)) {

         // pull apart the footer, getting the news feed source (^....\x0d\x0a)
         byte val_next = value[read++];
         byte val_peek =  (read < value.length) ? value[read+1] : 0x00;  // skip the new lines

         while (((val_next < (byte)0x30) || (val_next > (byte)0x39)) && (val_next != 0)) {  // consume all non-numerics first
            ftr_source += (char)(val_next & 0xff);  // convert the byte to an unsigned int
            val_next =  (read < value.length) ? value[read] : 0x00;  // attempt to read until end of stream
            read++;
            if (read > value.length) { break; }  // shouldn't ever hit this, but save a NPE
         }

         while ((val_next != LT) && (val_next != CR) && (val_next != LF) && (val_next != 0))  {  // get as much timedate as possible
            // this is an american format, so arrives as mm-dd-yy HHiizzz
            ftr_datetime += (char)(val_next & 0xff);  // convert the byte to an unsigned int
            val_next =  (read < value.length) ? value[read++] : 0x00;  // skip the new lines
            if (read > value.length) { break; }  // shouldn't ever hit this, but save a NPE
         }
         if (val_next == LT) {
            // hit the delimiter, carry on
            val_next =  (read < value.length) ? value[read++] : 0x00;
         }

         if (ftr_datetime.length() > 0) {
            // we want to pass this back in a more friendly format
            String format_out = "yyyy-MM-dd'T'HH:mm:ss'Z'";
            Date dateunix = new Date();
            try {
               // standard ap format
               String format_in = "MM-dd-yy HHmmzzz";

               if (FORMAT == this.FMT_IPTC_RTR) {
                  // standard reuters format
                  format_in = "HH:mm MM-dd-yy";
               }
               SimpleDateFormat dfi =   new SimpleDateFormat(format_in);
               dfi.setTimeZone(TimeZone.getTimeZone("UTC"));
               dateunix = dfi.parse(ftr_datetime);
            }
            catch (ParseException ep) {
               // failed, but this will just fall through to setting the date to now
            }
            SimpleDateFormat dfo =   new SimpleDateFormat(format_out);
            dfo.setTimeZone(TimeZone.getTimeZone("UTC"));
            ftr_datetime = dfo.format(dateunix);
         }
         while ((val_next == CR) || (val_next == LF)) {
            val_next =  (read < value.length) ? value[read++] : 0x00;  // skip the new lines
            if ((val_next != CR) && (val_next != LF)) {
               --read;
            }
         }
         done = true; // don't let this run back through and start thrashing metadata
      }

      properties.put("publisher", ftr_source);
      properties.put("created", ftr_datetime);
      properties.put("modified", ftr_datetime);

      added = (ftr_source.length() + ftr_datetime.length()) > 0; 
      return added;
   }


   private void setMetadata(Metadata metadata, HashMap<String,String> properties) {

      // every property that gets set must be non-null, or it will cause NPE
      // in other consuming applications, like Lucene
      metadata.set(Metadata.CONTENT_TYPE,  clean("text/anpa-1312"));
      metadata.set(TikaCoreProperties.TITLE,         clean(properties.get("title")));
      metadata.set(TikaCoreProperties.KEYWORDS,       clean(properties.get("subject")));
      metadata.set(TikaCoreProperties.CREATOR,        clean(properties.get("author")));
      metadata.set(TikaCoreProperties.CREATED, clean(properties.get("created")));
      metadata.set(TikaCoreProperties.MODIFIED,      clean(properties.get("modified")));
      metadata.set(TikaCoreProperties.SOURCE,      clean(properties.get("source")));
//      metadata.set(TikaCoreProperties.PUBLISHER,     clean(properties.get("publisher")));
      metadata.set(TikaCoreProperties.PUBLISHER,     clean(this.getFormatName()));

/*
        metadata.set(TikaCoreProperties.DATE, font.getHeader().getCreated().getTime());
        metadata.set(
                Property.internalDate(TikaCoreProperties.MODIFIED),
                font.getHeader().getModified().getTime());
*/
   }

   private String clean(String value) {
      if (value == null) {
         value = "";
      }

      value = value.replaceAll("``", "`");
      value = value.replaceAll("''", "'");
      value = value.replaceAll(new String(new char[] {SL}), "'");
      value = value.replaceAll(new String(new char[] {SR}), "'");
      value = value.replaceAll(new String(new char[] {DL}), "\"");
      value = value.replaceAll(new String(new char[] {DR}), "\"");
      value = value.trim();

      return (value);
   }
}
