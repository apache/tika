/* Copyright 2016 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.wordperfect;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * QuattroPro properties collection.
 * @author Pascal Essiembre
 */
public interface QuattroPro {
   public static final String QUATTROPRO_METADATA_NAME_PREFIX = "wordperfect";

   public static final String META_CREATOR = "creator";
   public static final String META_LAST_USER = "last-user";
   
   /**
    * ID. 
    */
   Property ID = Property.internalText(
           QUATTROPRO_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "Id");
   /**
    * Version. 
    */
   Property VERSION = Property.internalInteger(
           QUATTROPRO_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "Version");
   /**
    * Build. 
    */
   Property BUILD = Property.internalInteger(
           QUATTROPRO_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "Build");
   /**
    * Lowest version. 
    */
   Property LOWEST_VERSION = Property.internalInteger(
           QUATTROPRO_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "LowestVersion");
   /**
    * Number of pages. 
    */
   Property PAGE_COUNT = Property.internalInteger(
           QUATTROPRO_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "PageCount");
   /**
    * Creator. 
    */
   Property CREATOR = Property.internalText(
           QUATTROPRO_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "Creator");
   /**
    * Last User. 
    */
   Property LAST_USER = Property.internalText(
           QUATTROPRO_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "LastUser");
}
