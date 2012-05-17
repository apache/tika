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

/**
 * Contains a core set of basic Tika metadata properties, which all parsers
 *  will attempt to supply (where the file format permits). These are all
 *  defined in terms of other standard namespaces.
 *  
 * Users of Tika who wish to have consistent metadata across file formats
 *  can make use of these Properties, knowing that where present they will
 *  have consistent semantic meaning between different file formats. (No 
 *  matter if one file format calls it Title, another Long-Title and another
 *  Long-Name, if they all mean the same thing as defined by 
 *  {@link DublinCore#TITLE} then they will all be present as such)
 *
 * For now, most of these properties are composite ones including the deprecated
 *  non-prefixed String properties from the Metadata class. In Tika 2.0, most
 *  of these will revert back to simple assignments.
 */
@SuppressWarnings("deprecation")
public interface TikaCoreProperties {
    /**
     * @see DublinCore#FORMAT
     */
    public static final Property FORMAT = Property.composite(DublinCore.FORMAT, 
            new Property[] { Property.internalText(Metadata.FORMAT) });
    
   /**
    * @see DublinCore#IDENTIFIER
    */
   public static final Property IDENTIFIER = Property.composite(DublinCore.IDENTIFIER, 
            new Property[] { Property.internalText(Metadata.IDENTIFIER) });
    
   /**
    * @see DublinCore#CONTRIBUTOR
    */
    public static final Property CONTRIBUTOR = Property.composite(DublinCore.CONTRIBUTOR, 
            new Property[] { Property.internalText(Metadata.CONTRIBUTOR) });
    
   /**
    * @see DublinCore#COVERAGE
    */
    public static final Property COVERAGE = Property.composite(DublinCore.COVERAGE, 
            new Property[] { Property.internalText(Metadata.COVERAGE) });
    
   /**
    * @see DublinCore#CREATOR
    */
    public static final Property CREATOR = Property.composite(DublinCore.CREATOR, 
            new Property[] { Property.internalText(Metadata.CREATOR) });
    
    /**
     * @see Office#INITIAL_AUTHOR
     */
    public static final Property INITIAL_AUTHOR = Office.INITIAL_AUTHOR;

    /**
     * @see Office#LAST_AUTHOR
     */
    public static final Property LAST_AUTHOR = Property.composite(Office.LAST_AUTHOR,
            new Property[] { Property.internalText(MSOffice.LAST_AUTHOR) });
    
   /**
    * @see DublinCore#LANGUAGE
    */
    public static final Property LANGUAGE = Property.composite(DublinCore.LANGUAGE, 
            new Property[] { Property.internalText(Metadata.LANGUAGE) });
    
   /**
    * @see DublinCore#PUBLISHER
    */
    public static final Property PUBLISHER = Property.composite(DublinCore.PUBLISHER, 
            new Property[] { Property.internalText(Metadata.PUBLISHER) });
    
   /**
    * @see DublinCore#RELATION
    */
    public static final Property RELATION = Property.composite(DublinCore.RELATION, 
            new Property[] { Property.internalText(Metadata.RELATION) });
    
   /**
    * @see DublinCore#RIGHTS
    */
    public static final Property RIGHTS = Property.composite(DublinCore.RIGHTS, 
            new Property[] { Property.internalText(Metadata.RIGHTS) });
    
   /**
    * @see DublinCore#SOURCE
    */
    public static final Property SOURCE = Property.composite(DublinCore.SOURCE, 
            new Property[] { Property.internalText(Metadata.SOURCE) });
    
   /**
    * @see DublinCore#TYPE
    */
    public static final Property TYPE = Property.composite(DublinCore.TYPE, 
            new Property[] { Property.internalText(Metadata.TYPE) });

    
    // Descriptive properties
    
    /**
     * @see DublinCore#TITLE
     */
    public static final Property TITLE = Property.composite(DublinCore.TITLE, 
            new Property[] { Property.internalText(Metadata.TITLE) });
     
    /**
     * @see DublinCore#DESCRIPTION
     */
    public static final Property DESCRIPTION = Property.composite(DublinCore.DESCRIPTION, 
            new Property[] { Property.internalText(Metadata.DESCRIPTION) });
     
    /**
     * @see DublinCore#SUBJECT
     */
    public static final Property SUBJECT = Property.composite(DublinCore.SUBJECT, 
            new Property[] { Property.internalText(Metadata.SUBJECT) });
      
    /**
     * @see Office#KEYWORDS
     */
    public static final Property KEYWORDS = Property.composite(Office.KEYWORDS,
            new Property[] { Property.internalTextBag(MSOffice.KEYWORDS) });

    
    // Date related properties
    
    /**
     * @see DublinCore#DATE
     */
     public static final Property DATE = Property.composite(DublinCore.DATE, 
             new Property[] { Metadata.DATE });
     
    /**
     * @see DublinCore#MODIFIED
     */
     public static final Property MODIFIED = Property.composite(DublinCore.MODIFIED, 
             new Property[] { Property.internalText(Metadata.MODIFIED) });
     
     /** @see Office#CREATION_DATE */
     public static final Property CREATION_DATE = Property.composite(Office.CREATION_DATE,
             new Property[] { MSOffice.CREATION_DATE });

     /** @see Office#SAVE_DATE */
     public static final Property SAVE_DATE = Property.composite(Office.SAVE_DATE,
             new Property[] { MSOffice.LAST_SAVED });
     
     /** @see Office#PRINT_DATE */
     public static final Property PRINT_DATE = Property.composite(Office.PRINT_DATE, 
             new Property[] { MSOffice.LAST_PRINTED });
    
     
    // Geographic related properties
     
    /**
     * @see Geographic#LATITUDE
     */
    public static final Property LATITUDE = Geographic.LATITUDE;
    
    /**
     * @see Geographic#LONGITUDE
     */
    public static final Property LONGITUDE = Geographic.LONGITUDE;
    
    /**
     * @see Geographic#ALTITUDE
     */
    public static final Property ALTITUDE = Geographic.ALTITUDE;
}
