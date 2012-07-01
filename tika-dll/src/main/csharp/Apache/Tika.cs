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
using System;
using System.IO;

namespace Apache
{

    public class Tika
    {

        private readonly org.apache.tika.Tika tika = new org.apache.tika.Tika();

        public string detect(string name)
        {
            return tika.detect(name);
        }

        public string detect(FileInfo file)
        {
            return tika.detect(new java.io.File(file.FullName)); ;
        }

        public string detect(Uri uri)
        {
            return tika.detect(new java.net.URL(uri.AbsoluteUri));
        }

        public string parseToString(FileInfo file)
        {
            return tika.parseToString(new java.io.File(file.FullName)); ;
        }

        public string parseToString(Uri uri)
        {
            return tika.parseToString(new java.net.URL(uri.AbsoluteUri)); ;
        }

    }

}
