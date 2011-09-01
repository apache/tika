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
package org.apache.tika.parser.external;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.NullOutputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser that uses an external program (like catdoc or pdf2txt) to extract
 *  text content and metadata from a given document.
 */
public class ExternalParser extends AbstractParser {
    private static final long serialVersionUID = -1079128990650687037L;
    
    /**
     * The token, which if present in the Command string, will
     *  be replaced with the input filename. 
     * Alternately, the input data can be streamed over STDIN.
     */
    public static final String INPUT_FILE_TOKEN = "${INPUT}";
    /**
     * The token, which if present in the Command string, will
     *  be replaced with the output filename. 
     * Alternately, the output data can be collected on STDOUT.
     */
    public static final String OUTPUT_FILE_TOKEN = "${OUTPUT}";

    /**
     * Media types supported by the external program.
     */
    private Set<MediaType> supportedTypes = Collections.emptySet();
    
    /**
     * Regular Expressions to run over STDOUT to
     *  extract Metadata.
     */
    private Map<Pattern,String> metadataPatterns = null;

    /**
     * The external command to invoke.
     * @see Runtime#exec(String[])
     */
    private String[] command = new String[] { "cat" };

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return getSupportedTypes();
    }

    public Set<MediaType> getSupportedTypes() {
        return supportedTypes;
    }

    public void setSupportedTypes(Set<MediaType> supportedTypes) {
        this.supportedTypes =
            Collections.unmodifiableSet(new HashSet<MediaType>(supportedTypes));
    }


    public String[] getCommand() {
        return command;
    }

    /**
     * Sets the command to be run. This can include either of
     *  {@link #INPUT_FILE_TOKEN} or {@link #OUTPUT_FILE_TOKEN}
     *  if the command needs filenames.
     * @see Runtime#exec(String[])
     */
    public void setCommand(String... command) {
        this.command = command;
    }
    
    
    public Map<Pattern,String> getMetadataExtractionPatterns() {
       return metadataPatterns;
    }
    
    /**
     * Sets the map of regular expression patterns and Metadata
     *  keys. Any matching patterns will have the matching
     *  metadata entries set.
     * Set this to null to disable Metadata extraction.
     */
    public void setMetadataExtractionPatterns(Map<Pattern,String> patterns) {
       this.metadataPatterns = patterns;
    }
    

    /**
     * Executes the configured external command and passes the given document
     *  stream as a simple XHTML document to the given SAX content handler.
     * Metadata is only extracted if {@link #setMetadataExtractionPatterns(Map)}
     *  has been called to set patterns.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml =
            new XHTMLContentHandler(handler, metadata);

        TemporaryResources tmp = new TemporaryResources();
        try {
            parse(TikaInputStream.get(stream, tmp),
                    xhtml, metadata, tmp);
        } finally {
            tmp.dispose();
        }
    }

    private void parse(
            TikaInputStream stream, XHTMLContentHandler xhtml,
            Metadata metadata, TemporaryResources tmp)
            throws IOException, SAXException, TikaException {
        boolean inputToStdIn = true;
        boolean outputFromStdOut = true;
        boolean hasPatterns = (metadataPatterns != null && !metadataPatterns.isEmpty());

        File output = null;

        // Build our command
        String[] cmd = new String[command.length];
        System.arraycopy(command, 0, cmd, 0, command.length);
        for(int i=0; i<cmd.length; i++) {
           if(cmd[i].indexOf(INPUT_FILE_TOKEN) != -1) {
              cmd[i] = cmd[i].replace(INPUT_FILE_TOKEN, stream.getFile().getPath());
              inputToStdIn = false;
           }
           if(cmd[i].indexOf(OUTPUT_FILE_TOKEN) != -1) {
              output = tmp.createTemporaryFile();
              outputFromStdOut = false;
           }
        }

        // Execute
        Process process;
        if(cmd.length == 1) {
           process = Runtime.getRuntime().exec( cmd[0] );
        } else {
           process = Runtime.getRuntime().exec( cmd );
        }

        try {
            if(inputToStdIn) {
               sendInput(process, stream);
            } else {
               process.getOutputStream().close();
            }

            InputStream out = process.getInputStream();
            InputStream err = process.getErrorStream();
            
            if(hasPatterns) {
               extractMetadata(err, metadata);
               
               if(outputFromStdOut) {
                  extractOutput(out, xhtml);
               } else {
                  extractMetadata(out, metadata);
               }
            } else {
               ignoreStream(err);
               
               if(outputFromStdOut) {
                  extractOutput(out, xhtml);
               } else {
                  ignoreStream(out);
               }
            }
        } finally {
            try {
                process.waitFor();
            } catch (InterruptedException ignore) {
            }
        }

        // Grab the output if we haven't already
        if (!outputFromStdOut) {
            extractOutput(new FileInputStream(output), xhtml);
        }
    }

    /**
     * Starts a thread that extracts the contents of the standard output
     * stream of the given process to the given XHTML content handler.
     * The standard output stream is closed once fully processed.
     *
     * @param process process
     * @param xhtml XHTML content handler
     * @throws SAXException if the XHTML SAX events could not be handled
     * @throws IOException if an input error occurred
     */
    private void extractOutput(InputStream stream, XHTMLContentHandler xhtml)
            throws SAXException, IOException {
        Reader reader = new InputStreamReader(stream);
        try {
            xhtml.startDocument();
            xhtml.startElement("p");
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                xhtml.characters(buffer, 0, n);
            }
            xhtml.endElement("p");
            xhtml.endDocument();
        } finally {
            reader.close();
        }
    }

    /**
     * Starts a thread that sends the contents of the given input stream
     * to the standard input stream of the given process. Potential
     * exceptions are ignored, and the standard input stream is closed
     * once fully processed. Note that the given input stream is <em>not</em>
     * closed by this method.
     *
     * @param process process
     * @param stream input stream
     */
    private void sendInput(final Process process, final InputStream stream) {
        new Thread() {
            public void run() {
                OutputStream stdin = process.getOutputStream();
                try {
                    IOUtils.copy(stream, stdin);
                } catch (IOException e) {
                }
            }
        }.start();
    }

    /**
     * Starts a thread that reads and discards the contents of the
     * standard stream of the given process. Potential exceptions
     * are ignored, and the stream is closed once fully processed.
     *
     * @param process process
     */
    private void ignoreStream(final InputStream stream) {
        new Thread() {
            public void run() {
                try {
                    IOUtils.copy(stream, new NullOutputStream());
                } catch (IOException e) {
                } finally {
                    IOUtils.closeQuietly(stream);
                }
            }
        }.start();
    }
    
    private void extractMetadata(final InputStream stream, final Metadata metadata) {
       new Thread() {
          public void run() {
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
             try {
                String line;
                while ( (line = reader.readLine()) != null ) {
                   for(Pattern p : metadataPatterns.keySet()) {
                      Matcher m = p.matcher(line);
                      if(m.find()) {
                         metadata.add( metadataPatterns.get(p), m.group(1) );
                      }
                   }
                }
             } catch (IOException e) {
             } finally {
                IOUtils.closeQuietly(reader);
                IOUtils.closeQuietly(stream);
            }
          }
       }.start();
    }
    
    /**
     * Checks to see if the command can be run. Typically used with
     *  something like "myapp --version" to check to see if "myapp"
     *  is installed and on the path.
     *  
     * @param checkCmd The check command to run
     * @param errorValue What is considered an error value? 
     */
    public static boolean check(String checkCmd, int... errorValue) {
       return check(new String[] {checkCmd}, errorValue);
    }
    public static boolean check(String[] checkCmd, int... errorValue) {
       if(errorValue.length == 0) {
          errorValue = new int[] { 127 };
       }
       
       try {
          Process process;
          if(checkCmd.length == 1) {
             process = Runtime.getRuntime().exec(checkCmd[0]);
          } else {
             process = Runtime.getRuntime().exec(checkCmd);
          }
          int result = process.waitFor();
          
          for(int err : errorValue) {
             if(result == err) return false;
          }
          return true;
       } catch(IOException e) {
          // Some problem, command is there or is broken
          return false;
       } catch (InterruptedException ie) {
          // Some problem, command is there or is broken
          return false;
      }
    }
}
