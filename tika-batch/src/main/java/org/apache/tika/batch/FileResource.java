package org.apache.tika.batch;

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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

import java.io.IOException;
import java.io.InputStream;


/**
 * This is a basic interface to handle a logical "file".  
 * This should enable code-agnostic handling of files from different 
 * sources: file system, database, etc.
 *
 */
public interface FileResource {

  //The literal lowercased extension of a file.  This may or may not
  //have any relationship to the actual type of the file.
  public static final Property FILE_EXTENSION = Property.internalText("tika:file_ext");

  /**
   * This is only used in logging to identify which file
   * may have caused problems.  While it is probably best
   * to use unique ids for the sake of debugging, it is not 
   * necessary that the ids be unique.  This id
   * is never used as a hashkey by the batch processors, for example.
   * 
   * @return an id for a FileResource
   */
  public String getResourceId();
  
  /**
   * This gets the metadata available before the parsing of the file.
   * This will typically be "external" metadata: file name,
   * file size, file location, data stream, etc.  That is, things
   * that are known about the file from outside information, not
   * file-internal metadata.
   * 
   * @return Metadata
   */
  public Metadata getMetadata();
  
  /**
   * 
   * @return an InputStream for the FileResource
   * @throws java.io.IOException
   */
  public InputStream openInputStream() throws IOException;
  
}
