package org.apache.tika.batch.builders;


import java.util.Map;

import org.apache.tika.batch.ParserFactory;
import org.w3c.dom.Node;

public interface IParserFactoryBuilder {

    public ParserFactory build(Node node, Map<String, String> runtimeAttrs);
}
