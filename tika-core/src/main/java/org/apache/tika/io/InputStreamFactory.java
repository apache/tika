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
package org.apache.tika.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * A factory which returns a fresh {@link InputStream} for the <em>same</em> resource each time.
 *
 * <p>This is typically desired where it is easier / quicker / simpler to fetch a fresh {@link
 * InputStream} to re-read a given resource, rather than do any kind of buffering.
 *
 * <p>It is typically used with {@link TikaInputStream#get(InputStreamFactory)} when combined with a
 * Parser which needs to read the resource's stream multiple times when processing.
 */
public interface InputStreamFactory {
    InputStream getInputStream() throws IOException;
}
