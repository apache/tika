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
package org.apache.tika.batch.builders;

import java.util.Map;

import org.w3c.dom.Node;

import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.util.XMLDOMUtil;

/**
 * Builds BasicContentHandler with type defined by attribute "basicHandlerType"
 * with possible values: xml, html, text, body, ignore.
 * Default is text.
 * <p>
 * Sets the writeLimit to the value of "writeLimit.
 * Default is -1;
 */
public class DefaultContentHandlerFactoryBuilder implements IContentHandlerFactoryBuilder {

    @Override
    public ContentHandlerFactory build(Node node, Map<String, String> runtimeAttributes) {
        Map<String, String> attributes = XMLDOMUtil.mapifyAttrs(node, runtimeAttributes);
        BasicContentHandlerFactory.HANDLER_TYPE type = null;
        String handlerTypeString = attributes.get("basicHandlerType");
        type = BasicContentHandlerFactory
                .parseHandlerType(handlerTypeString, BasicContentHandlerFactory.HANDLER_TYPE.TEXT);
        int writeLimit = -1;
        String writeLimitString = attributes.get("writeLimit");
        if (writeLimitString != null) {
            try {
                writeLimit = Integer.parseInt(attributes.get("writeLimit"));
            } catch (NumberFormatException e) {
                //swallow and default to -1
                //TODO: should we throw a RuntimeException?
            }
        }
        return new BasicContentHandlerFactory(type, writeLimit);
    }


}
