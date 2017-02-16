package org.apache.tika.eval.tokens;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.FilteringTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.util.TokenFilterFactory;

/**
 * Creates a very narrowly focused TokenFilter that limits tokens based on length
 * _unless_ they've been identified as &lt;DOUBLE&gt; or &lt;SINGLE&gt;
 * by the CJKBigramFilter.
 *
 * This class is intended to be used when generating "common tokens" files.
 */
public class CJKBigramAwareLengthFilterFactory extends TokenFilterFactory {



    private final int min;
    private final int max;
    public CJKBigramAwareLengthFilterFactory(Map<String, String> args) {
        super(args);
        min = Integer.parseInt(args.get("min"));
        max = Integer.parseInt(args.get("max"));
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new CJKAwareLengthFilter(tokenStream);
    }

    private class CJKAwareLengthFilter extends FilteringTokenFilter {
        private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
        private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

        public CJKAwareLengthFilter(TokenStream in) {
            super(in);
        }

        @Override
        protected boolean accept() throws IOException {
            if ( termAtt.length() < min) {
                String type = typeAtt.type();
                if (type == CJKBigramFilter.DOUBLE_TYPE || type == CJKBigramFilter.SINGLE_TYPE) {
                    return true;
                }
            }
            return termAtt.length() >= min && termAtt.length() <= max;
        }
    }

    /*
    private static boolean isCJ(int codePoint) {
        if (
                (codePoint >= 0x4E00 && codePoint <= 0x9FFF) ||
                        ( codePoint >= 0x3400 && codePoint <= 0x4dbf) ||
                        ( codePoint >= 0x20000 && codePoint <= 0x2a6df) ||
                        ( codePoint >= 0x2A700 && codePoint <= 0x2b73f) ||
                        ( codePoint >= 0x2B740 && codePoint <= 0x2B81F) ||
                        ( codePoint >= 0x2B820 && codePoint <- 0x2CEAF) ||
                        ( codePoint >= 0xF900 && codePoint <= 0xFAFF) ||
                        ( codePoint >= 0x2F800 && codePoint <= 0x2Fa1F)
        ) {
            return true;
        }
        return false;
    }*/

}
