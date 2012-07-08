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
package org.apache.tika.parser.iwork;

/**
 * Utility class to allow for conversion from an integer to Roman numerals
 * or alpha-numeric symbols in line with Pages auto numbering formats.
 */
 class AutoPageNumberUtils {
	
	private static final String ALPHABET[] = { "A", "B", "C", "D", "E", "F", "G",
		"H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
		"U", "V", "W", "X", "Y", "Z" };
	
	private static final int MAX = 26; 

	public static String asAlphaNumeric(int i) {
		StringBuffer sbuff = new StringBuffer();
		int index = i % MAX;
		int ratio = i / MAX;
		
		if (index == 0) {
			ratio--;
			index = MAX;
		}
		
		for(int j = 0; j <= ratio; j++) {
			sbuff.append(ALPHABET[index - 1]);		}
		return sbuff.toString();
	}
	
	public static String asAlphaNumericLower(int i) {
		return asAlphaNumeric(i).toLowerCase();
	}
	
	/*
	 * Code copied from jena.apache.org.
	 * @see com.hp.hpl.jena.sparql.util.RomanNumeral
	 */
    public static String asRomanNumerals(int i) {
        if ( i <= 0 )
            throw new NumberFormatException("Roman numerals are 1-3999 ("+i+")") ;
        if ( i > 3999 )
            throw new NumberFormatException("Roman numerals are 1-3999 ("+i+")") ;
        StringBuffer sbuff = new StringBuffer() ;
        
        i = i2r(sbuff, i, "M", 1000, "CM", 900, "D", 500, "CD", 400 ) ;
        i = i2r(sbuff, i, "C", 100,  "XC", 90,  "L", 50,  "XL", 40 ) ;
        i = i2r(sbuff, i, "X", 10,   "IX", 9,   "V", 5,   "IV", 4) ;
        
        while ( i >= 1 )
        {
            sbuff.append("I") ;
            i -= 1 ;
        }
        return sbuff.toString() ;
            
        
    }
    
	public static String asRomanNumeralsLower(int i) {
		return asRomanNumerals(i).toLowerCase();
	}
    
    private static int i2r(StringBuffer sbuff, int i,
                           String tens,  int iTens, 
                           String nines, int iNines,
                           String fives, int iFives,
                           String fours, int iFours)
    {
        while ( i >= iTens )
        {
            sbuff.append(tens) ;
            i -= iTens ;
        }
        
        if ( i >= iNines )
        {
            sbuff.append(nines) ;
            i -= iNines;
        }

        if ( i >= iFives )
        {
            sbuff.append(fives) ;
            i -= iFives ;
        }
        if ( i >= iFours )
        {
            sbuff.append(fours) ;
            i -= iFours ;
        }
        return i ;
    }

}
