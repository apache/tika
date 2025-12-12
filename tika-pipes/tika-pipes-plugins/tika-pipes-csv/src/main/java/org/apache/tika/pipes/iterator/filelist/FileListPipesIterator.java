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
package org.apache.tika.pipes.iterator.filelist;

/**
 * Reads a list of file names/relative paths from a UTF-8 file.
 * One file name/relative path per line.  This path is used for the fetch key,
 * the id and the emit key.  If you need more customized control of the keys/ids,
 * consider using the jdbc pipes iterator or the csv pipes iterator.
 *
 * Skips empty lines and lines starting with '#'
 *
 * TODO: implement this class
 */
public class FileListPipesIterator {
}
