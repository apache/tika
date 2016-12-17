package org.apache.tika.parser.microsoft;

import org.apache.tika.config.Field;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;

/**
 * Intermediate layer to set {@link OfficeParserConfig} uniformly.
 */
public abstract class AbstractOfficeParser extends AbstractParser {

    private final OfficeParserConfig defaultOfficeParserConfig = new OfficeParserConfig();

    /**
     * Checks to see if the user has specified an {@link OfficeParserConfig}.
     * If so, no changes are made; if not, one is added to the context.
     *
     * @param parseContext
     */
    public void configure(ParseContext parseContext) {
        OfficeParserConfig officeParserConfig = parseContext.get(OfficeParserConfig.class, defaultOfficeParserConfig);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
    }

    /**
     * @see OfficeParserConfig#getIncludeDeletedContent
     *
     * @return
     */
    public boolean getIncludeDeletedContent() {
        return defaultOfficeParserConfig.getIncludeDeletedContent();
    }

    /**
     * @see OfficeParserConfig#getIncludeMoveFromContent()
     *
     * @return
     */

    public boolean getIncludeMoveFromContent() {
        return defaultOfficeParserConfig.getIncludeMoveFromContent();
    }

    /**
     * @see OfficeParserConfig#getUseSAXDocxExtractor()
     *
     * @return
     */
    public boolean getUseSAXDocxExtractor() {
        return defaultOfficeParserConfig.getUseSAXDocxExtractor();
    }


    @Field
    public void setIncludeDeletedContent(boolean includeDeletedConent) {
        defaultOfficeParserConfig.setIncludeDeletedContent(includeDeletedConent);
    }

    @Field
    public void setIncludeMoveFromContent(boolean includeMoveFromContent) {
        defaultOfficeParserConfig.setIncludeMoveFromContent(includeMoveFromContent);
    }

    @Field
    public void setUseSAXDocxExtractor(boolean useSAXDocxExtractor) {
        defaultOfficeParserConfig.setUseSAXDocxExtractor(useSAXDocxExtractor);
    }

    @Field
    public void setUseSAXPptxExtractor(boolean useSAXPptxExtractor) {
        defaultOfficeParserConfig.setUseSAXPptxExtractor(useSAXPptxExtractor);
    }
}
