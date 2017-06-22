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

package org.apache.tika.parser.digest;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;


public class CompositeDigester implements DigestingParser.Digester {

    private final DigestingParser.Digester[] digesters;

    public CompositeDigester(DigestingParser.Digester ... digesters) {
        this.digesters = digesters;
    }

    @Override
    public void digest(InputStream is, Metadata m, ParseContext parseContext) throws IOException {
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(is, tmp);
        try {
            for (DigestingParser.Digester digester : digesters) {
                digester.digest(tis, m, parseContext);
            }
        } finally {
            try {
                tmp.dispose();
            } catch (TikaException e) {
                throw new IOExceptionWithCause(e);
            }
        }
    }
}
