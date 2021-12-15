Welcome to Apache Tika  <https://tika.apache.org/>
=================================================

[![license](https://img.shields.io/github/license/apache/tika.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0)
[![Jenkins](https://img.shields.io/jenkins/s/https/ci-builds.apache.org/job/Tika/job/tika-main-jdk8.svg?maxAge=3600)](https://ci-builds.apache.org/job/Tika/job/tika-main-jdk8/)
[![Jenkins tests](https://img.shields.io/jenkins/t/https/ci-builds.apache.org/job/Tika/job/tika-main-jdk8.svg?maxAge=3600)](https://ci-builds.apache.org/job/Tika/job/tika-main-jdk8/lastBuild/testReport/)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.tika/tika.svg?maxAge=86400)](http://search.maven.org/#search|ga|1|g%3A%22org.apache.tika%22)

Apache Tika(TM) is a toolkit for detecting and extracting metadata and structured text content from various documents using existing parser libraries.

Tika is a project of the [Apache Software Foundation](https://www.apache.org).

Apache Tika, Tika, Apache, the Apache feather logo, and the Apache Tika project logo are trademarks of The Apache Software Foundation.

Getting Started
===============
Pre-built binaries of Apache Tika standalone applications are available
from https://tika.apache.org/download.html . Pre-built binaries of all the
Tika jars can be fetched from Maven Central or your favourite Maven mirror.

**Tika 1.X is scheduled for End of Life (EOL) on September 30, 2022.**  We will
continue to make security improvements until the EOL, but we do not plan to back port new functionality from the main/2.x branch. See [Migrating to 2.x](#migrating-to-2x) below for more details. 

Tika is based on **Java 8** and uses the [Maven 3](https://maven.apache.org) build system. 
**N.B.** [Docker](https://www.docker.com/products/personal) is required in the main/2.x branch to complete all unit tests. You can pass the `-DskipTests` flag if you wish to skip tests.

To build Tika from source, use the following command in the main directory:

    mvn clean install


The build consists of a number of components, including a standalone runnable jar that you can use to try out Tika features. You can run it like this:

    java -jar tika-app/target/tika-app-*.jar --help


To build a specific project (for example, tika-server-standard):

    mvn clean install -am -pl :tika-server-standard


Migrating to 2.x
================
The initial 2.x release notes are available in the [archives](https://archive.apache.org/dist/tika/2.0.0/CHANGES-2.0.0.txt).

See our [wiki](https://cwiki.apache.org/confluence/display/TIKA/Migrating+to+Tika+2.0.0) for the latest.

Contributing via Github
=======================
See the [pull request template](https://github.com/apache/tika/blob/main/.github/pull_request_template.md).

## Thanks to all the people who have contributed

[![contributors](https://contributors-img.web.app/image?repo=apache/tika)](https://github.com/apache/tika/graphs/contributors)

Building from a Specific Tag
============================
Let's assume that you want to build the 1.22 tag:
```
0. Download and install hub.github.com
1. git clone https://github.com/apache/tika.git 
2. cd tika
3. git checkout 1.22
4. mvn clean install
```

If a new vulnerability has been discovered between the date of the 
tag and the date you are building the tag, you may need to build with:

```
4. mvn clean install -Dossindex.fail=false
```

If a local test is not working in your environment, please notify
 the project at dev@tika.apache.org. As an immediate workaround, 
 you can turn off individual tests with e.g.: 

```
4. mvn clean install -Dossindex.fail=false -Dtest=\!UnpackerResourceTest#testPDFImages
```

License (see also LICENSE.txt)
==============================

Collective work: Copyright 2011 The Apache Software Foundation.

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at

<https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.

Apache Tika includes a number of subcomponents with separate copyright notices and license terms. Your use of these subcomponents is subject to the terms and conditions of the licenses listed in the LICENSE.txt file.

Export Control
==============

This distribution includes cryptographic software.  The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.  BEFORE using any encryption software, please  check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to  see if this is permitted.  See <http://www.wassenaar.org/> for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms.  The form and manner of this Apache Software Foundation distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

The following provides more details on the included cryptographic software:

Apache Tika uses the Bouncy Castle generic encryption libraries for extracting text content and metadata from encrypted PDF files.  See <http://www.bouncycastle.org/> for more details on Bouncy Castle.  

Mailing Lists
=============

Discussion about Tika takes place on the following mailing lists:

* user@tika.apache.org    - About using Tika
* dev@tika.apache.org     - About developing Tika

Notification on all code changes are sent to the following mailing list:

* commits@tika.apache.org

The mailing lists are open to anyone and publicly archived.

You can subscribe the mailing lists by sending a message to 
[LIST]-subscribe@tika.apache.org (for example user-subscribe@...).  
To unsubscribe, send a message to [LIST]-unsubscribe@tika.apache.org.  
For more instructions, send a message to [LIST]-help@tika.apache.org.

Issue Tracker
=============

If you encounter errors in Tika or want to suggest an improvement or a new feature,
 please visit the [Tika issue tracker](https://issues.apache.org/jira/browse/TIKA). 
 There you can also find the latest information on known issues and 
 recent bug fixes and enhancements.

Build Issues
============

*TODO*

* Need to install jce

* If you find any other issues while building, please email the dev@tika.apache.org
  list.
