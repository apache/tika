=================================================
Welcome to Apache Tika  <http://tika.apache.org/>
=================================================

Apache Tika(TM) is a toolkit for detecting and extracting metadata and
structured text content from various documents using existing parser
libraries.

Tika is a project of the Apache Software Foundation <http://www.apache.org/>.

Apache Tika, Tika, Apache, the Apache feather logo, and the Apache Tika
project logo are trademarks of The Apache Software Foundation.

Getting Started
===============

Tika is based on Java 5 and uses the Maven 2 <http://maven.apache.org/>
build system. To build Tika, use the following command in this directory:

    mvn clean install

The build consists of a number of components, including a standalone runnable
jar that you can use to try out Tika features. You can run it like this:

    java -jar tika-app/target/tika-app-*.jar --help

License (see also LICENSE.txt)
==============================

Collective work: Copyright 2011 The Apache Software Foundation.

Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Apache Tika includes a number of subcomponents with separate copyright
notices and license terms. Your use of these subcomponents is subject to
the terms and conditions of the licenses listed in the LICENSE.txt file.

Export control
==============

This distribution includes cryptographic software.  The country in  which
you currently reside may have restrictions on the import,  possession, use,
and/or re-export to another country, of encryption software.  BEFORE using
any encryption software, please  check your country's laws, regulations and
policies concerning the import, possession, or use, and re-export of
encryption software, to  see if this is permitted.  See
<http://www.wassenaar.org/> for more information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS), has classified this software as Export Commodity Control
Number (ECCN) 5D002.C.1, which includes information security software using
or performing cryptographic functions with asymmetric algorithms.  The form
and manner of this Apache Software Foundation distribution makes it eligible
for export under the License Exception ENC Technology Software Unrestricted
(TSU) exception (see the BIS Export Administration Regulations, Section
740.13) for both object code and source code.

The following provides more details on the included cryptographic software:

    Apache Tika uses the Bouncy Castle generic encryption libraries for
    extracting text content and metadata from encrypted PDF files.
    See http://www.bouncycastle.org/ for more details on Bouncy Castle.

Mailing Lists
=============

Discussion about Tika takes place on the following mailing lists:

    user@tika.apache.org    - About using Tika
    dev@tika.apache.org     - About developing Tika

Notification on all code changes are sent to the following mailing list:

    commits@tika.apache.org

The mailing lists are open to anyone and publicly archived.

You can subscribe the mailing lists by sending a message to
<LIST>-subscribe@tika.apache.org (for example user-subscribe@...).
To unsubscribe, send a message to <LIST>-unsubscribe@tika.apache.org.
For more instructions, send a message to <LIST>-help@tika.apache.org.

Issue Tracker
=============

If you encounter errors in Tika or want to suggest an improvement or
a new feature, please visit the Tika issue tracker at
https://issues.apache.org/jira/browse/TIKA. There you can also find the
latest information on known issues and recent bug fixes and enhancements.
