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
package org.apache.tika.parser.executable;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for executable files. Currently supports ELF and PE
 */
public class ExecutableParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = 32128791892482l;

    // TODO Put these somewhere more general
    public static final String MACHINE_x86_32 = "x86-32";
    public static final String MACHINE_x86_64 = "x86-64";
    public static final String MACHINE_IA_64 = "IA-64";
    public static final String MACHINE_UNKNOWN = "Unknown";
    // TODO The rest
    public static Property MACHINE_TYPE = Property.internalClosedChoise("machine", 
          new String[] { MACHINE_x86_32, MACHINE_x86_64, MACHINE_UNKNOWN });
    
    public static Property ARCHITECTURE = Property.internalClosedChoise("architecture", 
          new String[] { "32", "64" });
    
    public static final class Endian {
       private String name;
       private boolean msb;
       public String getName() { return name; }
       public boolean isMSB() { return msb; }
       public String getMSB() { if(msb) { return "MSB"; } else { return "LSB"; } }
       private Endian(String name, boolean msb) { this.name = name; this.msb = msb; }
       
       public static final Endian LITTLE_ENDIAN = new Endian("Little", false);
       public static final Endian BIG_ENDIAN = new Endian("Big", true);
    }
    public static Property ENDIAN = Property.internalClosedChoise("endian", 
          new String[] { Endian.LITTLE_ENDIAN.name, Endian.BIG_ENDIAN.name });
    
    private static final MediaType PE_EXE = MediaType.application("x-msdownload");
    private static final MediaType ELF_GENERAL = MediaType.application("x-elf");
    private static final MediaType ELF_OBJECT = MediaType.application("x-object");
    private static final MediaType ELF_EXECUTABLE = MediaType.application("x-executable");
    private static final MediaType ELF_SHAREDLIB = MediaType.application("x-sharedlib");
    private static final MediaType ELF_COREDUMP = MediaType.application("x-coredump");
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
            		PE_EXE,
                  ELF_GENERAL,
                  ELF_OBJECT, ELF_EXECUTABLE, ELF_SHAREDLIB, ELF_COREDUMP
            )));
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // We only do metadata, for now
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        // What kind is it?
        byte[] first4 = new byte[4];
        IOUtils.readFully(stream, first4);
        
        if (first4[0] == (byte)'M' && first4[1] == (byte)'Z') {
           parsePE(xhtml, metadata, stream, first4);
        } else if (first4[0] == (byte)0x7f && first4[1] == (byte)'E' &&
                   first4[2] == (byte)'L' && first4[3] == (byte)'F') {
           parseELF(xhtml, metadata, stream, first4);
        }
        
        
        // Finish everything
        xhtml.endDocument();
    }

    /**
     * Parses a DOS or Windows PE file
     */
    public void parsePE(XHTMLContentHandler xhtml, Metadata metadata,
          InputStream stream, byte[] first4) throws TikaException, IOException {
       metadata.add(Metadata.CONTENT_TYPE, PE_EXE.toString());
       
       // Skip over the MS-DOS bit
       byte[] msdosSection = new byte[0x3c-4];
       IOUtils.readFully(stream, msdosSection);
       
       // Grab the PE header offset
       int peOffset = LittleEndian.readInt(stream);
       
       // Sanity check - while it may go anywhere, it's normally in the first few kb
       if (peOffset > 4096 || peOffset < 0x3f) return;
       
       // Skip the rest of the MS-DOS stub (if PE), until we reach what should
       //  be the PE header (if this is a PE executable)
       stream.skip(peOffset - 0x40);
       
       // Read the PE header
       byte[] pe = new byte[24];
       IOUtils.readFully(stream, pe);
       
       // Check it really is a PE header
       if (pe[0] == (byte)'P' && pe[1] == (byte)'E' && pe[2]==0 && pe[3]==0) {
          // Good, has a valid PE signature
       } else {
          // Old style MS-DOS
          return;
       }
       
       // Read the header values
       int machine    = LittleEndian.getUShort(pe, 4);
       int numSectors = LittleEndian.getUShort(pe, 6);
       long createdAt = LittleEndian.getInt(pe, 8);
       long symbolTableOffset = LittleEndian.getInt(pe, 12);
       long numSymbols = LittleEndian.getInt(pe, 16);
       int sizeOptHdrs = LittleEndian.getUShort(pe, 20);
       int characteristcs = LittleEndian.getUShort(pe, 22);
       
       // Turn this into helpful metadata
       Date createdAtD = new Date(createdAt*1000l);
       metadata.set(Metadata.CREATION_DATE, createdAtD);
       
       switch(machine) {
         case 0x14c:
            metadata.set(MACHINE_TYPE, MACHINE_x86_32);
            metadata.set(ENDIAN, Endian.LITTLE_ENDIAN.name);
            metadata.set(ARCHITECTURE, "32");
            break;

         case 0x8664:
            metadata.set(MACHINE_TYPE, MACHINE_x86_32);
            metadata.set(ENDIAN, Endian.LITTLE_ENDIAN.name);
            metadata.set(ARCHITECTURE, "64");
            break;

         case 0x200:
            metadata.set(MACHINE_TYPE, MACHINE_IA_64);
            metadata.set(ENDIAN, Endian.LITTLE_ENDIAN.name);
            metadata.set(ARCHITECTURE, "64");
            break;
            
         default:
            metadata.set(MACHINE_TYPE, MACHINE_UNKNOWN);
            break;
       }
    }

    /**
     * Parses a Unix ELF file
     */
    public void parseELF(XHTMLContentHandler xhtml, Metadata metadata,
          InputStream stream, byte[] first4) throws TikaException, IOException {
       // Byte 5 is the architecture
       int architecture = stream.read();
       if (architecture == 1) {
          metadata.set(ARCHITECTURE, "32");
       } else if (architecture == 2) {
          metadata.set(ARCHITECTURE, "64");          
       }
       
       // Byte 6 is the endian-ness
       int endian = stream.read();
       if (endian == 1) {
          metadata.set(ENDIAN, Endian.LITTLE_ENDIAN.name);
       } else if (endian == 2) {
          metadata.set(ENDIAN, Endian.BIG_ENDIAN.name);
       }
       
       // Byte 7 is the elf version
       int elfVer = stream.read();
       
       // Byte 8 is the OS
       // Byte 9 is the OS (specific) ABI version
       int os = stream.read();
       int osVer = stream.read();
       
       // Bytes 10-16 are padding and lengths
       byte[] padLength = new byte[7];
       IOUtils.readFully(stream, padLength);
       
       // Bytes 16-17 are the object type (LE/BE)
       int type;
       if (endian == 1) {
          type = EndianUtils.readUShortLE(stream);
       } else {
          type = EndianUtils.readUShortBE(stream);
       }
       switch(type) {
         case 1:
            metadata.add(Metadata.CONTENT_TYPE, ELF_OBJECT.toString());
            break;
            
         case 2:
            metadata.add(Metadata.CONTENT_TYPE, ELF_EXECUTABLE.toString());
            break;
            
         case 3:
            metadata.add(Metadata.CONTENT_TYPE, ELF_SHAREDLIB.toString());
            break;
            
         case 4:
            metadata.add(Metadata.CONTENT_TYPE, ELF_COREDUMP.toString());
            break;
            
         default:
            metadata.add(Metadata.CONTENT_TYPE, ELF_GENERAL.toString());
            break;
       }
                 
       // Bytes 18-19 are the machine (EM_*)
       int machine;
       if (endian == 1) {
          machine = EndianUtils.readUShortLE(stream);
       } else {
          machine = EndianUtils.readUShortBE(stream);
       }
       switch(machine) {
         case 3:
            metadata.set(MACHINE_TYPE, MACHINE_x86_32);
            break;
         case 50:
            metadata.set(MACHINE_TYPE, MACHINE_IA_64);
            break;
         case 62:
            metadata.set(MACHINE_TYPE, MACHINE_x86_64);
            break;
       }
       
       // Bytes 20-23 are the version
       // TODO
    }
}
