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
package org.apache.tika.metadata;

/**
 * WordPerfect properties collection.
 * @author Pascal Essiembre
 */
public interface WordPerfect {
   public static final String WORDPERFECT_METADATA_NAME_PREFIX = "wordperfect";

   /**
    * File size as defined in document header. 
    */
   Property FILE_SIZE = Property.internalText(
           WORDPERFECT_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "FileSize");
   /**
    * File identifier. 
    */
   Property FILE_ID = Property.internalText(
           WORDPERFECT_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "FileId");
   /**
    * Product type. 
    */
   Property PRODUCT_TYPE = Property.internalInteger(
           WORDPERFECT_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "ProductType");
   /**
    * File type. 
    */
   Property FILE_TYPE = Property.internalInteger(
           WORDPERFECT_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "FileType");
   /**
    * Major version. 
    */
   Property MAJOR_VERSION = Property.internalInteger(
           WORDPERFECT_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "MajorVersion");
   /**
    * Minor version. 
    */
   Property MINOR_VERSION = Property.internalInteger(
           WORDPERFECT_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "MinorVersion");
   /**
    * Is encrypted?. 
    */
   Property ENCRYPTED = Property.internalBoolean(
           WORDPERFECT_METADATA_NAME_PREFIX
                   + Metadata.NAMESPACE_PREFIX_DELIMITER + "Encrypted");
}
