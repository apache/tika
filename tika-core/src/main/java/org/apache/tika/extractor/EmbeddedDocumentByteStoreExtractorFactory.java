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
package org.apache.tika.extractor;


/**
 * This factory creates EmbeddedDocumentExtractors that require an
 * {@link EmbeddedDocumentBytesHandler} in the
 * {@link org.apache.tika.parser.ParseContext} should extend this.
 *
 * This is a shim interface to signal to {@link org.apache.tika.pipes.PipesServer}
 * to use the {@link @RUnpackExtractor} if the user doesn't configure a custom
 * EmbeddedDocumentExtractor.
 *
 * TODO: Figure out how to simplify this and allow for emitting of the source document.
 */
public interface EmbeddedDocumentByteStoreExtractorFactory extends EmbeddedDocumentExtractorFactory {

}
