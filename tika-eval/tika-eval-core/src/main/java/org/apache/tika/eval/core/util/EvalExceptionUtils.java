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
package org.apache.tika.eval.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import org.apache.tika.utils.ExceptionUtils;

public class EvalExceptionUtils {

    //these remove runtime info from the stacktraces so
    //that actual causes can be counted.
    private final static Pattern CAUSED_BY_SNIPPER =
            Pattern.compile("(Caused by: [^:]+):[^\\r\\n]+");

    public static String normalize(String stacktrace) {
        if (StringUtils.isBlank(stacktrace)) {
            return "";
        }
        String sortTrace = ExceptionUtils.trimMessage(stacktrace);

        Matcher matcher = CAUSED_BY_SNIPPER.matcher(sortTrace);
        sortTrace = matcher.replaceAll("$1");
        return sortTrace.replaceAll("org.apache.tika.", "o.a.t.");
    }
}
