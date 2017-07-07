
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

import org.apache.maven.settings.Proxy as MvnProxy
import java.net.Proxy as JDKProxy
import groovy.transform.Field

//BEGIN: Global context ; ${settings} is injected by the plugin
List<MvnProxy> mvnProxies = settings.getProxies()?.findAll{it.isActive()}
@Field JDKProxy proxy = null
if (mvnProxies && mvnProxies.size() > 0) {
    mvnProxy = mvnProxies.get(0)
    println "Using the first Proxy setting : ${mvnProxy.username}@ ${mvnProxy.host} : ${mvnProxy.port} "
    proxy = new JDKProxy(JDKProxy.Type.HTTP, new InetSocketAddress(mvnProxy.host, mvnProxy.port))
    Authenticator.setDefault(new Authenticator(){
        @Override
        protected PasswordAuthentication getPasswordAuthentication(){
            return new PasswordAuthentication(mvnProxy.username, mvnProxy.password?.toCharArray())
        }
    })
    println "Proxy is configured"
}
//END : Global Context

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
    inStr.close()
    outStr.close()
}

/**
 * Downloads file
 * @param urlStr url of file
 * @param file path to store file
 * @return
 */
def downloadFile(String urlStr, File file) {
    println "GET : $urlStr -> $file (Using proxy? ${proxy != null})"
    url = new URL(urlStr)

    urlConn =  proxy ? url.openConnection(proxy) : url.openConnection()
    contentLength = urlConn.getContentLengthLong()

    file.getParentFile().mkdirs()
    inStream = urlConn.getInputStream()
    outStream = new FileOutputStream(file)
    copyWithProgress(inStream, outStream, contentLength)
    outStream.close()
    inStream.close()
    println "Download Complete.."
}

def urlPrefix = "http://opennlp.sourceforge.net/models-1.5"
def prefixPath = "src/test/resources/org/apache/tika/parser/ner/opennlp/"
def ageUrlPrefix = "https://raw.githubusercontent.com/USCDataScience/AgePredictor/master/model"
def agePrefixPath = "src/test/resources/org/apache/tika/parser/recognition/"

// detecting proper path for test resources
if (new File("tika-parsers").exists() && new File("tika-app").exists()  ) {
    // running from parent maven project, but resources should go to sub-module
    prefixPath = "tika-parsers/" + prefixPath
    agePrefixPath = "tika-parsers/" + agePrefixPath
}

def modelFiles = //filePath : url
        [(prefixPath + "ner-person.bin"): (urlPrefix + "/en-ner-person.bin"),
          (prefixPath + "ner-location.bin"): (urlPrefix + "/en-ner-location.bin"),
          (prefixPath + "ner-organization.bin"): (urlPrefix + "/en-ner-organization.bin"),
          (prefixPath + "en-pos-maxent.bin"): (urlPrefix + "/en-pos-maxent.bin"),
          (prefixPath + "en-sent.bin"): (urlPrefix + "/en-sent.bin"),
          (prefixPath + "en-token.bin"): (urlPrefix + "/en-token.bin"),
          (prefixPath + "ner-date.bin"): (urlPrefix + "/en-ner-date.bin"),
          (agePrefixPath + "classify-bigram.bin"): (ageUrlPrefix + "/classify-bigram.bin"),
          (agePrefixPath + "regression-global.bin"): (ageUrlPrefix + "/regression-global.bin")]

for (def entry : modelFiles) {
    File file = new File(entry.key)
    if (!file.exists()) {
        downloadFile(entry.value, file)
    }
}