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
 * QuattroPro properties collection.
 * @author Pascal Essiembre
 */
public interface QuattroPro {
   public static final String QUATTROPRO_METADATA_NAME_PREFIX = "wordperfect";

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
}