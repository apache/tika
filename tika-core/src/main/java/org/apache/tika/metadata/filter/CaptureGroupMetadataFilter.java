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
package org.apache.tika.metadata.filter;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.StringUtils;


/**
 * This filter runs a regex against the first value in the "sourceField".
 * If the pattern matches, it extracts the first group of the first match and
 * set's the "targetField"'s value to that first group.
 * <p/>
 * If there is a match, this will overwrite whatever value is in the
 * "targetField".
 * <p/>
 * If there is not a match, this filter will be a no-op.
 * <p/>
 * If there are multiple matches, this filter will capture only the first.
 * Open a ticket if you need different behavior.
 * <p/>
 * If the source field has multiple values, this will run the regex
 * against only the first value.
 * <p/>
 * If the source field does not exist, this filter will be a no-op.
 * <p/>
 * If the target field is the same value as the source field, this filter
 * will overwrite the value in that field. Again, if there are multiple
 * values in that field, those will all be overwritten.
 */
public class CaptureGroupMetadataFilter extends MetadataFilter implements Initializable {

    private String regexString;
    private Pattern regex;
    private String sourceField;
    private String targetField;

    @Override
    public void filter(Metadata metadata) throws TikaException {
        String val = metadata.get(sourceField);
        if (StringUtils.isBlank(val)) {
            return;
        }
        Matcher m = regex.matcher(val);
        if (m.find()) {
            metadata.set(targetField, m.group(1));
        }
    }

    @Field
    public void setRegex(String regex) {
        this.regexString = regex;
    }

    @Field
    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    @Field
    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

    public String getRegex() {
        return regexString;
    }

    public String getSourceField() {
        return sourceField;
    }

    public String getTargetField() {
        return targetField;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            regex = Pattern.compile(regexString);
        } catch (PatternSyntaxException e) {
            throw new TikaConfigException("Couldn't parse regex", e);
        }

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (StringUtils.isBlank(sourceField)) {
            throw new TikaConfigException("Must specify a 'sourceField'");
        }
        if (StringUtils.isBlank(targetField)) {
            throw new TikaConfigException("Must specify a 'targetField'");
        }
    }
}
