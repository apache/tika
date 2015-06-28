package org.apache.tika.batch.builders;

import java.util.Locale;
import java.util.Map;

import org.apache.tika.batch.ParserFactory;
import org.apache.tika.util.ClassLoaderUtil;
import org.apache.tika.util.XMLDOMUtil;
import org.w3c.dom.Node;

public class ParserFactoryBuilder implements IParserFactoryBuilder {


    @Override
    public ParserFactory build(Node node, Map<String, String> runtimeAttrs) {
        Map<String, String> localAttrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttrs);
        String className = localAttrs.get("class");
        ParserFactory pf = ClassLoaderUtil.buildClass(ParserFactory.class, className);

        if (localAttrs.containsKey("parseRecursively")) {
            String bString = localAttrs.get("parseRecursively").toLowerCase(Locale.ENGLISH);
            if (bString.equals("true")) {
                pf.setParseRecursively(true);
            } else if (bString.equals("false")) {
                pf.setParseRecursively(false);
            } else {
                throw new RuntimeException("parseRecursively must have value of \"true\" or \"false\": "+
                bString);
            }
        }
        return pf;
    }
}
