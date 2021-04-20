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
package org.apache.tika.parser.pdf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;

class PDFDOMUtil {

    /**
     * This recursively looks through cosBase for cosdictionary's that have
     * a type key in the types set. It intentionally does not follow /p, /parent, /page
     * dictionaries.
     *
     * @param cosBase
     * @param types
     * @param maxDepth
     * @return
     */
    static List<COSDictionary> findType(COSBase cosBase, Set<COSName> types, int maxDepth) {
        List<COSDictionary> found = new ArrayList<>();
        Set<COSBase> seen = new HashSet<>();
        find(cosBase, types, 0, maxDepth, seen, found);
        return found;
    }

    private static void find(COSBase cosBase, Set<COSName> types, int depth, int maxDepth,
                             Set<COSBase> seen, List<COSDictionary> found) {
        if (seen.contains(cosBase)) {
            return;
        }
        if (depth >= maxDepth) {
            return;
        }
        seen.add(cosBase);
        if (cosBase instanceof COSObject) {
            COSBase dereferencedBase = ((COSObject)cosBase).getObject();
            find(dereferencedBase, types, depth + 1, maxDepth, seen, found);
        } else if (cosBase instanceof COSDictionary) {
            COSDictionary dict = (COSDictionary)cosBase;
            COSName value = dict.getCOSName(COSName.TYPE);
            if (value != null && types.contains(value)) {
                found.add(dict);
            } else if (value != null && (value.equals(COSName.P) || value.equals(COSName.PAGE)
                    || value.equals(COSName.PARENT))) {
                //don't descend page, p, or parent
                return;
            } else {
                for (Map.Entry<COSName, COSBase> e : dict.entrySet()) {
                    find(e.getValue(), types, depth + 1, maxDepth, seen, found);
                }
            }
        } else if (cosBase instanceof COSArray) {
            for (COSBase item : ((COSArray)cosBase)) {
                find(item, types, depth + 1, maxDepth, seen, found);
            }
        }
    }
}
