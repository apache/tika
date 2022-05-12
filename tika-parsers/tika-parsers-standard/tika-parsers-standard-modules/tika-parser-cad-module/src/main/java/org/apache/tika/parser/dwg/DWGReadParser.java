package org.apache.tika.parser.dwg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonReadFeature;


public class DWGReadParser extends AbstractDWGParser {
    private static final Logger LOG = LoggerFactory.getLogger(DWGParser.class);
    /**
     * 
     */
    private static final long serialVersionUID = 7983127145030096837L;
    private static MediaType TYPE = MediaType.image("vnd.dwg");

    public Set < MediaType > getSupportedTypes(ParseContext context) {
        return Collections.singleton(TYPE);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
    throws IOException, SAXException, TikaException {

        configure(context);
        DWGParserConfig dwgc = context.get(DWGParserConfig.class);
        final XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        xhtml.startDocument();
        UUID uuid = UUID.randomUUID();
        File tmpFileOut = File.createTempFile(uuid + "dwgreadout", ".json");
        File tmpFileOutCleaned = File.createTempFile(uuid + "dwgreadoutclean", ".json");
        File tmpFileIn = File.createTempFile(uuid + "dwgreadin", ".dwg");
        try {

            FileUtils.copyInputStreamToFile(stream, tmpFileIn);

            List < String > command = Arrays.asList(dwgc.getDwgReadExecutable(), "-O", "JSON", "-o",
                tmpFileOut.getCanonicalPath(), tmpFileIn.getCanonicalPath());
            Process p = new ProcessBuilder(command).start();

            try {
                int exitCode = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (dwgc.isCleanDwgReadOutput()) {
                // dwgread sometimes creates strings with invalid utf-8 sequences or invalid json (nan instead of NaN). replace them
                // with empty string.

                try (FileInputStream fis = new FileInputStream(tmpFileOut); FileOutputStream fos = new FileOutputStream(tmpFileOutCleaned)) {
                    byte[] bytes = new byte[dwgc.getCleanDwgReadOutputBatchSize()];
                    while (fis.read(bytes) != -1) {
                        byte[] fixedBytes = new String(bytes, StandardCharsets.UTF_8)
                            .replaceAll(dwgc.getCleanDwgReadRegexToReplace(), dwgc.getCleanDwgReadReplaceWith())
                            .getBytes(StandardCharsets.UTF_8);
                        fos.write(fixedBytes, 0, fixedBytes.length);
                    }
                } finally {
                    FileUtils.deleteQuietly(tmpFileOut);
                    tmpFileOut = tmpFileOutCleaned;
                }

            }
            
            // we can't guarantee the json output is correct so we try to ignore as many errors as we can
            JsonFactory jfactory = JsonFactory.builder().enable(JsonReadFeature.ALLOW_MISSING_VALUES,JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER).build();
            JsonParser jParser = jfactory.createParser(tmpFileOut);
            JsonToken nextToken = jParser.nextToken();
            while ((nextToken = jParser.nextToken()) != JsonToken.END_OBJECT) {
                if (nextToken == JsonToken.FIELD_NAME) {
                    String nextFieldName = jParser.currentName();
                    nextToken = jParser.nextToken();
                    if (nextToken.isStructStart()) {

                        if ("OBJECTS".equals(nextFieldName)) {
                            // Start array
                            jParser.nextToken();
                            while (jParser.nextToken() != JsonToken.END_ARRAY) {
                                parseDwgObject(jParser, (nextTextValue) -> {

                                    try {
										xhtml.characters(cleanupDwgString(nextTextValue));
										xhtml.newline();
									} catch (SAXException e) {
										LOG.error("Could not write next text value {} to xhtml stream", nextTextValue);
									}
                                });
                            }
                        }  else if ("FILEHEADER".equals(nextFieldName)) {
                            parseHeader(jParser,metadata);
                        } else {
                            jParser.skipChildren();
                        }
                    }
                }
            }
            jParser.close();
        } finally {
            FileUtils.deleteQuietly(tmpFileOut);
            FileUtils.deleteQuietly(tmpFileIn);
        }
        
        
        xhtml.endDocument();
    }
    private void parseDwgObject(JsonParser jsonParser, Consumer<String> textConsumer) throws IOException {
        JsonToken nextToken;
        while ((nextToken = jsonParser.nextToken()) != JsonToken.END_OBJECT) {
            if (nextToken == JsonToken.FIELD_NAME) {
                String nextFieldName = jsonParser.currentName();
                nextToken = jsonParser.nextToken();
                if (nextToken.isStructStart()) {
                    jsonParser.skipChildren();
                } else if (nextToken.isScalarValue()) {
                    if ("text".equals(nextFieldName)) {
                        String textVal = jsonParser.getText();
                        if (StringUtils.isNotBlank(textVal)) {

                            textConsumer.accept(textVal);
                        }
                    }
                    else    if ("text_value".equals(nextFieldName)) {
                        String textVal = jsonParser.getText();
                        if (StringUtils.isNotBlank(textVal)) {

                            textConsumer.accept(textVal);
                            
                        }
                    }
                }
            }
        }
    }
    private void parseHeader(JsonParser jsonParser, Metadata metadata) throws IOException {
        JsonToken nextToken;
        while ((nextToken = jsonParser.nextToken()) != JsonToken.END_OBJECT) {
            if (nextToken == JsonToken.FIELD_NAME) {
                String nextFieldName = jsonParser.currentName();
                nextToken = jsonParser.nextToken();
                if (nextToken.isStructStart()) {
                    jsonParser.skipChildren();
                } else if (nextToken.isScalarValue()) {
                    metadata.set(nextFieldName, jsonParser.getText());
                }
            }
        }
    }
	private String cleanupDwgString(String dwgString) {
		//Cleaning chars have been found from the following websites:
		//https://www.cadforum.cz/en/text-formatting-codes-in-mtext-objects-tip8640
		//https://adndevblog.typepad.com/autocad/2017/09/dissecting-mtext-format-codes.html
		String cleanString;
		//replace Alignment characters
		cleanString = dwgString.replaceAll("(?<!\\\\\\\\)\\\\A[0-2];", "");
		//remove \\p and replace with new line
		cleanString = cleanString.replaceAll("(?<!\\\\\\\\)\\\\P", "\n");
		//replace pi
		cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pi.*;", "");
		//replace pxi
		cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pxi.*;", "");
		//replace pxt
		cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pxt.*;", "");
		//replace lines with \H text height
		cleanString = cleanString.replaceAll("(?<!\\\\)\\\\H[0-9]*.*;", "");
		//replace lines with \F Font Selection
		cleanString = cleanString.replaceAll("(?<!\\\\)\\\\F.*;", "");
		//replace lines without \L.l
		//cleanString = cleanString.replaceAll("\\\\H[0-9]*\\.[0-9]*x;", "");
		//replace Starting formating
		//cleanString = cleanString.replaceAll("\\{\\\\L", "");
		//replace }
		//cleanString = cleanString.replaceAll("\\}", "");

		
		
		//
		return cleanString;
		
	}

}