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

class FileConsumerFutureResult implements IFileProcessorFutureResult {

  private final FileStarted fileStarted;
  private final int filesProcessed;
  
  public FileConsumerFutureResult(FileStarted fs, int filesProcessed) {
    this.fileStarted = fs;
    this.filesProcessed = filesProcessed;
  }
  
  public FileStarted getFileStarted() {
    return fileStarted;
  }
  
  public int getFilesProcessed() {
    return filesProcessed;
  }
}
