package org.apache.tika.parser.microsoft;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import java.util.Deque;
import java.util.EnumSet;
import java.util.Locale;

public class FormattingUtils {
    private FormattingUtils() {
    }

    /**
     * Closes all tags until {@code currentState} contains only tags from {@code desired} set,
     * then open all required tags to reach desired state.
     *
     * @param xhtml        handler
     * @param desired      desired formatting state
     * @param currentState current formatting state (stack of open formatting tags)
     * @throws SAXException pass underlying handler exception
     */
    public static void ensureFormattingState(XHTMLContentHandler xhtml,
                                             EnumSet<Tag> desired,
                                             Deque<Tag> currentState) throws SAXException {
        EnumSet<FormattingUtils.Tag> undesired = EnumSet.complementOf(desired);

        while (!currentState.isEmpty() && currentState.stream().anyMatch(undesired::contains)) {
            xhtml.endElement(currentState.pop().tagName());
        }

        desired.removeAll(currentState);
        for (FormattingUtils.Tag tag : desired) {
            currentState.push(tag);
            xhtml.startElement(tag.tagName());
        }
    }

    /**
     * Closes all formatting tags.
     *
     * @param xhtml           handler
     * @param formattingState current formatting state (stack of open formatting tags)
     * @throws SAXException pass underlying handler exception
     */
    public static void closeStyleTags(XHTMLContentHandler xhtml,
                                      Deque<Tag> formattingState) throws SAXException {
        ensureFormattingState(xhtml, EnumSet.noneOf(Tag.class), formattingState);
    }

    public static EnumSet<Tag> toTags(XWPFRun run) {
        EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
        if (run.isBold()) {
            tags.add(Tag.B);
        }
        if (run.isItalic()) {
            tags.add(Tag.I);
        }
        if (run.isStrikeThrough()) {
            tags.add(Tag.S);
        }
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            tags.add(Tag.U);
        }
        return tags;
    }

    public enum Tag {
        // DON'T reorder elements to avoid breaking tests: EnumSet is iterated in natural order
        // as enum variants are declared
        B, I, S, U;

        public String tagName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
