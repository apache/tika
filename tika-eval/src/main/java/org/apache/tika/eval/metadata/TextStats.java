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
package org.apache.tika.eval.metadata;

import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

public interface TextStats {

    String PREFIX = "tika_eval"+ TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;
    Property OOV = Property.internalReal(PREFIX+"oov");
    Property NUM_TOKENS = Property.internalInteger(PREFIX+"numTokens");
    Property NUM_UNIQUE_TOKENS = Property.internalInteger(PREFIX+"numUniqueTokens");
    Property NUM_ALPHABETIC_TOKENS = Property.internalInteger(PREFIX+"numAlphabeticTokens");
    Property NUM_UNIQUE_ALPHABETIC_TOKENS = Property.internalInteger(PREFIX+"numUniqueAlphabeticTokens");
    Property COMMON_TOKENS_LANG = Property.internalText(PREFIX+"commonTokensLang");
    Property NUM_COMMON_TOKENS = Property.internalInteger(PREFIX+"numCommonTokens");
    Property NUM_UNIQUE_COMMON_TOKENS = Property.internalInteger(PREFIX+"numUniqueCommonTokens");
    Property LANG_ID_1 = Property.internalText(PREFIX+"langid_1");
    Property LANG_ID_1_CONFIDENCE = Property.internalReal(PREFIX+"langid_1_conf");
    Property LANG_ID_2 = Property.internalText(PREFIX+"langid_2");
    Property LANG_ID_2_CONFIDENCE = Property.internalReal(PREFIX+"langid_2_conf");

    Property TOKEN_ENTROPY_RATE = Property.internalText(PREFIX+"tokenEntropyRate");
    Property TOKEN_LENGTH_SUM = Property.internalText(PREFIX+"tokenLengthSum");
    Property TOKEN_LENGTH_MEAN = Property.internalText(PREFIX+"tokenLengthMean");
    Property TOKEN_LENGTH_STD_DEV = Property.internalText(PREFIX+"tokenLengthStdDev");

}
