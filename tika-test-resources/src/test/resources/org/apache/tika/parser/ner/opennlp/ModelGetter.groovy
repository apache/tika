
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

/*
 * This file downloads Apache OpenNLP NER models for testing the NamedEntityParser
 */

import org.apache.commons.io.IOUtils

/**
 * Copies input stream to output stream, additionally printing the progress.
 * NOTE: this is optimized for large content
 * @param inStr source stream
 * @param outStr target stream
 * @param totalLength the total length of the content (used to calculate progress)
 * @return
 */
def copyWithProgress(InputStream inStr, OutputStream outStr, long totalLength){
    int PROGRESS_DELAY = 1000;
    byte[] buffer = new byte[1024 * 4]
    long count = 0
    int len
    long tt = System.currentTimeMillis()
    while ((len = inStr.read(buffer)) > 0) {
        outStr.write(buffer, 0, len)
        count += len
        if (System.currentTimeMillis() - tt > PROGRESS_DELAY) {
            println "${count * 100.0/totalLength}% : $count bytes of $totalLength"
            tt = System.currentTimeMillis()
        }
    }
    println "Copy complete. "
    IOUtils.closeQuietly(inStr)
    IOUtils.closeQuietly(outStr)
}

/**
 * Downloads file
 * @param urlStr url of file
 * @param file path to store file
 * @return
 */
def downloadFile(String urlStr, File file) {
    println "GET : $urlStr -> $file"
    urlConn = new URL(urlStr).openConnection()
    contentLength = urlConn.getContentLengthLong()

    file.getParentFile().mkdirs()
    inStream = urlConn.getInputStream()
    outStream = new FileOutputStream(file)
    copyWithProgress(inStream, outStream, contentLength)
    IOUtils.closeQuietly(outStream)
    IOUtils.closeQuietly(inStream)
    println "Download Complete.."
}


def urlPrefix = "http://opennlp.sourceforge.net/models-1.5"
def prefixPath = "src/test/resources/org/apache/tika/parser/ner/opennlp/"

// detecting proper path for test resources
if (new File("tika-test-resources").exists() && new File("tika-app").exists()  ) {
    // running from parent maven project, but resources should go to sub-module
    prefixPath = "tika-test-resources/" + prefixPath
}

def modelFiles = //filePath : url
        [ (prefixPath + "ner-person.bin"): (urlPrefix + "/en-ner-person.bin"),
          (prefixPath + "ner-location.bin"): (urlPrefix + "/en-ner-location.bin"),
          (prefixPath + "ner-organization.bin"): (urlPrefix + "/en-ner-organization.bin"),
          (prefixPath + "ner-date.bin"): (urlPrefix + "/en-ner-date.bin")]

for (def entry : modelFiles) {
    File file = new File(entry.key)
    if (!file.exists()) {
        downloadFile(entry.value, file)
    }
}