package org.apache.tika.metadata; /*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ import org.apache.tika.metadata.Metadata; import org.apache.tika.metadata.Property; public interface 
RTFMetadata {
    public static final String PREFIX_RTF_META = "rtf_meta";
    
    
    public static final String RTF_PICT_META_PREFIX = "rtf_pict:";
    
    /**
     * if set to true, this means that an image file is probably a "thumbnail"
     * any time a pict/emf/wmf is in an object
     */
    Property THUMBNAIL = Property.internalBoolean(PREFIX_RTF_META+
            Metadata.NAMESPACE_PREFIX_DELIMITER+"thumbnail");
    
    /**
     * if an application and version is given as part of the
     * embedded object, this is the literal string
     */
    Property EMB_APP_VERSION = Property.internalText(PREFIX_RTF_META+
            Metadata.NAMESPACE_PREFIX_DELIMITER+"emb_app_version");
    
    Property EMB_CLASS = Property.internalText(PREFIX_RTF_META+
            Metadata.NAMESPACE_PREFIX_DELIMITER+"emb_class");
    
    Property EMB_TOPIC = Property.internalText(PREFIX_RTF_META+
            Metadata.NAMESPACE_PREFIX_DELIMITER+"emb_topic");
    
    Property EMB_ITEM = Property.internalText(PREFIX_RTF_META+
            Metadata.NAMESPACE_PREFIX_DELIMITER+"emb_item");
    
}
