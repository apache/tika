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
package org.apache.tika.pipes.pipesiterator.filelist;

/**
 * Reads a list of file names/relative paths from a UTF-8 file.
 * One file name/relative path per line.  This path is used for the fetch key,
 * the id and the emit key.  If you need more customized control of the keys/ids,
 * consider using the jdbc pipes iterator or the csv pipes iterator.
 *
 * Skips empty lines and lines starting with '#'
 *
 *
 */
public class FileListPipesIterator {}
//TODO -- this next
/*extends PipesIteratorBase {

    @Field
    private String fileList;

    @Field
    private boolean hasHeader = false;

    private Path fileListPath;

    @Override
    protected void enqueue() throws IOException, TimeoutException, InterruptedException {
        try (BufferedReader reader = Files.newBufferedReader(fileListPath, StandardCharsets.UTF_8)) {
            if (hasHeader) {
                reader.readLine();
            }
            String line = reader.readLine();
            while (line != null) {
                if (! line.startsWith("#") && !StringUtils.isBlank(line)) {
                    FetchKey fetchKey = new FetchKey(getFetcherId(), line);
                    EmitKey emitKey = new EmitKey(getEmitterId(), line);
                    ParseContext parseContext = new ParseContext();
                    parseContext.set(HandlerConfig.class, getHandlerConfig());
                    tryToAdd(new FetchEmitTuple(line, fetchKey, emitKey,
                            new Metadata(), parseContext, getOnParseException()));
                }
                line = reader.readLine();
            }
        }
    }


    @Field
    public void setFileList(String path) {
        this.fileList = path;
    }

    @Field
    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        //these should all be fatal
        TikaConfig.mustNotBeEmpty("fileList", fileList);
        TikaConfig.mustNotBeEmpty("fetcherId", getFetcherId());
        TikaConfig.mustNotBeEmpty("emitterId", getEmitterId());

        fileListPath = Paths.get(fileList);
        if (!Files.isRegularFile(fileListPath)) {
            throw new TikaConfigException("file list " + fileList + " does not exist. " +
                    "Must specify an existing file");
        }
    }
}

 */
