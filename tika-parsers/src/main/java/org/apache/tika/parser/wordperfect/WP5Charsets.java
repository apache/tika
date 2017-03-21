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
package org.apache.tika.parser.wordperfect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WordPerfect 5.x constant values used for mapping WordPerfect charsets to
 * unicode equivalents when possible.
 * @author Pascal Essiembre
 */
final class WP5Charsets {
    private static final Logger LOG = LoggerFactory.getLogger(WP5Charsets.class);

    /**
     * Extended character sets used when fixed-length multi-byte functions
     * with a byte value of 192 (0xC0) are found in a WordPerfect document.
     * Those character set codes may be specific to WordPerfect 
     * file specifications and may or may not be considered standard 
     * outside WordPerfect. Applies to version 5.x.
     */
    public static final char[][] EXTENDED_CHARSETS = new char[][] {
        // WP Charset 0: ASCII (same as WP6)
        WP6Charsets.EXTENDED_CHARSETS[0],
        // WP Charset 1: Multinational 1 (same as WP6)
        WP6Charsets.EXTENDED_CHARSETS[1],
        // WP Charset 2: Multinational 2 (28 chars)
        {
        '\u0323','\u0324','\u02da','\u0325','\u02bc','\u032d','\u2017','\u005f',
        '\u0138','\u032e','\u033e','\u2018','\u0020','\u02bd','\u02db','\u0327',
        '\u0321','\u0322','\u030d','\u2019','\u0329','\u0020','\u0621','\u02be',
        '\u0306','\u0310','\u2032','\u2034'            
        },
        // WP Charset 3: Box Drawing (same as WP6)
        WP6Charsets.EXTENDED_CHARSETS[3],
        // WP Charset 4: Typographic Symbols (same as WP6)
        WP6Charsets.EXTENDED_CHARSETS[4],
        // WP Charset 5: Iconic Symbol (35 chars)
        {
        '\u2665','\u2666','\u2663','\u2660','\u2642','\u2640','\u263c','\u263a',
        '\u263b','\u266a','\u266c','\u25ac','\u2302','\u203c','\u221a','\u21a8',
        '\u2310','\u2319','\u25d8','\u25d9','\u21b5','\u261e','\u261c','\u2713',
        '\u2610','\u2612','\u2639','\u266f','\u266d','\u266e','\u260e','\u231a',
        '\u231b','\u2104','\u23b5'            
        },
        // WP Charset 6: Math/Scientific (same as WP6)
        WP6Charsets.EXTENDED_CHARSETS[6],
        // WP Charset 7 Math/Scientific Extended (same as WP6)
        WP6Charsets.EXTENDED_CHARSETS[7],
        // WP Charset 8: Greek (210 chars)
        {
        '\u0391','\u03b1','\u0392','\u03b2','\u0392','\u03d0','\u0393','\u03b3',
        '\u0394','\u03b4','\u0395','\u03b5','\u0396','\u03b6','\u0397','\u03b7',
        '\u0398','\u03b8','\u0399','\u03b9','\u039a','\u03ba','\u039b','\u03bb',
        '\u039c','\u03bc','\u039d','\u03bd','\u039e','\u03be','\u039f','\u03bf',
        '\u03a0','\u03c0','\u03a1','\u03c1','\u03a3','\u03c3','\u03f9','\u03db',
        '\u03a4','\u03c4','\u03a5','\u03c5','\u03a6','\u03d5','\u03a7','\u03c7',
        '\u03a8','\u03c8','\u03a9','\u03c9','\u03ac','\u03ad','\u03ae','\u03af',
        '\u03ca','\u03cc','\u03cd','\u03cb','\u03ce','\u03b5','\u03d1','\u03f0',
        '\u03d6','\u1fe5','\u03d2','\u03c6','\u03c9','\u037e','\u0387','\u0384',
        '\u00a8','\u0385','\u1fed','\u1fef','\u1fc0','\u1fbd','\u1fbf','\u1fbe',
        '\u1fce','\u1fde','\u1fcd','\u1fdd','\u1fcf','\u1fdf','\u0384','\u1fef',
        '\u1fc0','\u1fbd','\u1fbf','\u1fce','\u1fde','\u1fcd','\u1fdd','\u1fcf',
        '\u1fdf','\u1f70','\u1fb6','\u1fb3','\u1fb4','\u1fb7','\u1f00','\u1f04',
        '\u1f02','\u1f06','\u1f80','\u1f84','\u1f86','\u1f01','\u1f05','\u1f03',
        '\u1f07','\u1f81','\u1f85','\u1f87','\u1f72','\u1f10','\u1f14','\u1f13',
        '\u1f11','\u1f15','\u1f13','\u1f74','\u1fc6','\u1fc3','\u1fc4','\u1fc2',
        '\u1fc7','\u1f20','\u1f24','\u1f22','\u1f26','\u1f90','\u1f94','\u1f96',
        '\u1f21','\u1f25','\u1f23','\u1f27','\u1f91','\u1f95','\u1f97','\u1f76',
        '\u1fd6','\u0390','\u1fd2','\u1f30','\u1f34','\u1f32','\u1f36','\u1f31',
        '\u1f35','\u1f33','\u1f37','\u1f78','\u1f40','\u1f44','\u1f42','\u1f41',
        '\u1f45','\u1f43','\u1f7a','\u1fe6','\u03b0','\u1fe3','\u1f50','\u1f54',
        '\u1f52','\u1f56','\u1f51','\u1f55','\u1f53','\u1f57','\u1f7c','\u1ff6',
        '\u1ff3','\u1ff4','\u1ff2','\u1ff7','\u1f60','\u1f64','\u1f62','\u1f66',
        '\u1fa0','\u1fa4','\u1fa6','\u1f61','\u1f65','\u1f63','\u1f67','\u1fa1',
        '\u1fa5','\u1fa7','\u0374','\u0375','\u03db','\u03dd','\u03d9','\u03e1',
        '\u0386','\u0388','\u0389','\u038a','\u038c','\u038e','\u038f','\u03aa',
        '\u03ab','\u1fe5'
        },
        // WP Charset 9: Hebrew (119 chars)
        {
        '\u05d0','\u05d1','\u05d2','\u05d3','\u05d4','\u05d5','\u05d6','\u05d7',
        '\u05d8','\u05d9','\u05da','\u05db','\u05dc','\u05dd','\u05de','\u05df',
        '\u05e0','\u05e1','\u05e2','\u05e3','\u05e4','\u05e5','\u05e6','\u05e7',
        '\u05e8','\u05e9','\u05ea','\u05be','\u05c0','\u05c3','\u05f3','\u05f4',
        '\u05b0','\u05b1','\u05b2','\u05b3','\u05b4','\u05b5','\u05b6','\u05b7',
        '\u05b8','\u05b9','\u05ba','\u05bb','\u05bc','\u05bd','\u05bf','\u05b7',
        '\ufbe1','\u05f0','\u05f1','\u05f2','\u0591','\u0596','\u05ad','\u05a4',
        '\u059a','\u059b','\u05a3','\u05a5','\u05a6','\u05a7','\u09aa','\u0592',
        '\u0593','\u0594','\u0595','\u0597','\u0598','\u0599','\u05a8','\u059c',
        '\u059d','\u059e','\u05a1','\u05a9','\u05a0','\u059f','\u05ab','\u05ac',
        '\u05af','\u05c4','\u0544','\u05d0','\ufb31','\ufb32','\ufb33','\ufb34',
        '\ufb35','\ufb4b','\ufb36','\u05d7','\ufb38','\ufb39','\ufb3b','\ufb3a',
        '\u05da','\u05da','\u05da','\u05da','\u05da','\u05da','\ufb3c','\ufb3e',
        '\ufb40','\u05df','\ufb41','\ufb44','\ufb46','\ufb47','\ufb2b','\ufb2d',
        '\ufb2a','\ufb2c','\ufb4a','\ufb4c','\ufb4e','\ufb1f','\ufb1d'
        },
        // WP Charset 10: Cyrillic (150 chars)
        {
        '\u0410','\u0430','\u0411','\u0431','\u0412','\u0432','\u0413','\u0433',
        '\u0414','\u0434','\u0415','\u0435','\u0401','\u0451','\u0416','\u0436',
        '\u0417','\u0437','\u0418','\u0438','\u0419','\u0439','\u041a','\u043a',
        '\u041b','\u043b','\u041c','\u043c','\u041d','\u043d','\u041e','\u043e',
        '\u041f','\u043f','\u0420','\u0440','\u0421','\u0441','\u0422','\u0442',
        '\u0423','\u0443','\u0424','\u0444','\u0425','\u0445','\u0426','\u0446',
        '\u0427','\u0447','\u0428','\u0448','\u0429','\u0449','\u042a','\u044a',
        '\u042b','\u044b','\u042c','\u044c','\u042d','\u044d','\u042e','\u044e',
        '\u042f','\u044f','\u0490','\u0491','\u0402','\u0452','\u0403','\u0453',
        '\u0404','\u0454','\u0405','\u0455','\u0406','\u0456','\u0407','\u0457',
        '\u0408','\u0458','\u0409','\u0459','\u040a','\u045a','\u040b','\u045b',
        '\u040c','\u045c','\u040e','\u045e','\u040f','\u045f','\u0462','\u0463',
        '\u0472','\u0473','\u0474','\u0475','\u046a','\u046b','\ua640','\ua641',
        '\u0429','\u0449','\u04c0','\u04cf','\u0466','\u0467','\u0000','\u0000',
        '\u0000','\u0000','\u0000','\u0000','\u0000','\u0000','\u0000','\u0000',
        '\u0000','\u0000','\u0000','\u0000','\u0000','\u0000','\u0000','\u0000',
        '\u0000','\u0000','\u0400','\u0450','\u0000','\u0000','\u040d','\u045d',
        '\u0000','\u0000','\u0000','\u0000','\u0000','\u0000','\u0000','\u0000',
        '\u0000','\u0000','\u0000','\u0000','\u0301','\u0300'
        },
        // WP Charset 11: Japanese (185 chars)
        {
        '\u3041','\u3043','\u3045','\u3047','\u3049','\u3053','\u3083','\u3085',
        '\u3087','\u3094','\u3095','\u3096','\u3042','\u3044','\u3046','\u3048',
        '\u304a','\u304b','\u304d','\u3047','\u3051','\u3053','\u304c','\u304e',
        '\u3050','\u3052','\u3054','\u3055','\u3057','\u3059','\u305b','\u305d',
        '\u3056','\u3058','\u305a','\u305c','\u305e','\u305f','\u3051','\u3064',
        '\u3066','\u3068','\u3060','\u3062','\u3065','\u3067','\u3069','\u306a',
        '\u306b','\u306c','\u306d','\u306e','\u306f','\u3072','\u3075','\u3078',
        '\u307b','\u3070','\u3073','\u3076','\u3079','\u307c','\u3071','\u3074',
        '\u3077','\u307a','\u307d','\u307e','\u307f','\u3080','\u3081','\u3082',
        '\u3084','\u3086','\u3088','\u3089','\u308a','\u308b','\u308c','\u308d',
        '\u308e','\u3092','\u3093','\u3014','\u3015','\uff3b','\uff3d','\u300c',
        '\u300d','\u300c','\u300d','\u302a','\u3002','\u3001','\u309d','\u309e',
        '\u3003','\u30fc','\u309b','\u309c','\u30a1','\u30a3','\u30a5','\u30a7',
        '\u30a9','\u30c3','\u30e3','\u30e5','\u3057','\u30f4','\u30f5','\u30f6',
        '\u30a2','\u30a4','\u30a6','\u30a8','\u30aa','\u30ab','\u30ad','\u30af',
        '\u30b1','\u30b3','\u30ac','\u30ae','\u30b0','\u30b2','\u30b4','\u30b5',
        '\u30c4','\u30b9','\u30bb','\u30bd','\u30b6','\u30b8','\u30ba','\u30bc',
        '\u30be','\u30bf','\u30c1','\u30c4','\u30c6','\u30c8','\u30c0','\u30c2',
        '\u30c5','\u30c7','\u30c9','\u30ca','\u30cb','\u30cc','\u30cd','\u30ce',
        '\u30cf','\u30d2','\u30d5','\u30d8','\u03d0','\u30db','\u30d3','\u30d6',
        '\u30d9','\u30dc','\u30d1','\u30d4','\u30d7','\u30da','\u30dd','\u30de',
        '\u30df','\u30e0','\u30e1','\u30e2','\u30e4','\u30e6','\u30e8','\u30e9',
        '\u30ea','\u30ab','\u30ec','\u30ed','\u30ef','\u30f2','\u30f3','\u30fd',
        '\u30fe'
        },
        // WP Charset 12: User-defined (255 chars)
        {  
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',
        ' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' ',' '
        }
    }; 

    //TODO map multi-characters
    
    /**
     * Constructor.
     */
    private WP5Charsets() {
    }

    public static void append(StringBuilder out, int charset, int charval) {
        if (charset >= WP5Charsets.EXTENDED_CHARSETS.length) {
            LOG.debug("Unsupported WordPerfect 5.x charset: {}", charset);
            out.append(' ');
        } else if (charval >= WP5Charsets.EXTENDED_CHARSETS[charset].length) {
            LOG.debug("Unsupported WordPerfect 5.x charset ({}) character value: {}", charset, charval);
            out.append(' ');
        } else {
            out.append(WP5Charsets.EXTENDED_CHARSETS[charset][charval]);
        }
    }
}
