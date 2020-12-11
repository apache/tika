package org.apache.tika.server.core.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.server.core.ParseContextConfig;

import javax.ws.rs.core.MultivaluedMap;

public class DocumentSelectorConfig implements ParseContextConfig {

    public static final String X_TIKA_SKIP_EMBEDDED_HEADER = "X-Tika-Skip-Embedded";

    @Override
    public void configure(MultivaluedMap<String, String> httpHeaders,
                          Metadata mtadata, ParseContext context) {
        DocumentSelector documentSelector = null;
        for (String key : httpHeaders.keySet()) {
            if (StringUtils.endsWithIgnoreCase(key, X_TIKA_SKIP_EMBEDDED_HEADER)) {
                String skipEmbedded = httpHeaders.getFirst(key);
                if (Boolean.parseBoolean(skipEmbedded)) {
                    documentSelector = metadata -> false;
                }
            }
        }
        if (documentSelector != null) {
            context.set(DocumentSelector.class, documentSelector);
        }
    }
}
