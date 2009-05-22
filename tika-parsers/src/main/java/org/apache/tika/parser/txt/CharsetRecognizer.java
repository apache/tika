/**
*******************************************************************************
* Copyright (C) 2005, International Business Machines Corporation and         *
* others. All Rights Reserved.                                                *
*******************************************************************************
*/
package org.apache.tika.parser.txt;

/**
 * Abstract class for recognizing a single charset.
 * Part of the implementation of ICU's CharsetDetector.
 * 
 * Each specific charset that can be recognized will have an instance
 * of some subclass of this class.  All interaction between the overall
 * CharsetDetector and the stuff specific to an individual charset happens
 * via the interface provided here.
 * 
 * Instances of CharsetDetector DO NOT have or maintain 
 * state pertaining to a specific match or detect operation.
 * The WILL be shared by multiple instances of CharsetDetector.
 * They encapsulate const charset-specific information.
 * 
 * @internal
 */
abstract class CharsetRecognizer {
    /**
     * Get the IANA name of this charset.
     * @return the charset name.
     */
    abstract String      getName();
    
    /**
     * Get the ISO language code for this charset.
     * @return the language code, or <code>null</code> if the language cannot be determined.
     */
    public   String      getLanguage()
    {
        return null;
    }
    
    /**
     * Test the match of this charset with the input text data
     *      which is obtained via the CharsetDetector object.
     * 
     * @param det  The CharsetDetector, which contains the input text
     *             to be checked for being in this charset.
     * @return     Two values packed into one int  (Damn java, anyhow)
     *             <br/>
     *             bits 0-7:  the match confidence, ranging from 0-100
     *             <br/>
     *             bits 8-15: The match reason, an enum-like value.
     */
    abstract int         match(CharsetDetector det);

}
