package org.apache.tika.metadata.serialization;

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

public class PrettyMetadataKeyComparator implements java.util.Comparator<String> {
    @Override
    public int compare(String s1, String s2) {
        if (s1 == null) {
            return 1;
        } else if (s2 == null) {
            return -1;
        }

        //this is stinky.  This should reference RecursiveParserWrapper.TIKA_CONTENT
        //but that would require making core a dependency of serialization...
        //do we want to do that?
        if (s1.equals("tika:content")) {
            if (s2.equals("tika:content")) {
                return 0;
            }
            return 2;
        } else if (s2.equals("tika:content")) {
            return -2;
        }
        //do we want to lowercase?
        return s1.compareTo(s2);
    }
}

