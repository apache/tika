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
package org.apache.tika.metadata;

public interface XMPMM {

    String NAMESPACE_URI = "http://ns.adobe.com/xap/1.0/mm/";

    String PREFIX = "xmpMM";

    /** The xmpMM prefix followed by the colon delimiter */
    String PREFIX_ = PREFIX + ":";

    /**
     * A reference to the resource from which this one is derived.
     * This should be a minimal reference, in which missing
     * components can be assumed to be unchanged.
     * 
     * TODO This property is of type RessourceRef which is a struct
     */
//    Property DERIVED_FROM = Property.externalText(PREFIX_ + "DerivedFrom");

    /**
     * The common identifier for all versions and renditions of a resource.
     */
    Property DOCUMENTID = Property.externalText(PREFIX_ + "DocumentID");

    /**
     * An identifier for a specific incarnation of a resource, updated
     * each time a file is saved.
     */
    Property INSTANCEID = Property.externalText(PREFIX_ + "InstanceID");

    /**
     * The common identifier for the original resource from which
     * the current resource is derived. For example, if you save a
     * resource to a different format, then save that one to another
     * format, each save operation should generate a new
     * xmpMM:DocumentID that uniquely identifies the resource in
     * that format, but should retain the ID of the source file here.
     */
    Property ORIGINAL_DOCUMENTID = Property.externalText(
            PREFIX_ + "OriginalDocumentID");

    /**
     * The rendition class name for this resource. This property
     * should be absent or set to default for a resource that is not
     * a derived rendition
     */
    Property RENDITION_CLASS = Property.externalOpenChoise(
            PREFIX_ + "RenditionClass",
            "default", "draft", "low-res", "proof", "screen", "thumbnail");

    /**
     * Can be used to provide additional rendition parameters that
     * are too complex or verbose to encode in xmpMM:RenditionClass
     */
    Property RENDITION_PARAMS = Property.externalText(
            PREFIX_ + "RenditionParams");

}
