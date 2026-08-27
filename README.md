# Welcome to Apache Tika <https://tika.apache.org/>

[![license](https://img.shields.io/github/license/apache/tika.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0)
[![Jenkins](https://img.shields.io/jenkins/s/https/ci-builds.apache.org/job/Tika/job/tika-main-jdk17.svg?maxAge=3600)](https://ci-builds.apache.org/job/Tika/job/tika-main-jdk17/)
[![Jenkins tests](https://img.shields.io/jenkins/t/https/ci-builds.apache.org/job/Tika/job/tika-main-jdk17.svg?maxAge=3600)](https://ci-builds.apache.org/job/Tika/job/tika-main-jdk17/lastBuild/testReport/)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.tika/tika.svg?maxAge=86400)](http://search.maven.org/#search|ga|1|g%3A%22org.apache.tika%22)

Apache Tika(TM) detects and extracts metadata and text from over a thousand
file types. As of 4.0.0 it emits **Markdown by default** — output shaped for
LLM and RAG pipelines — parses in **crash-isolated forked processes**, and
adds vision-language-model parsers (Claude, Gemini, OpenAI) for documents OCR
can't read.

Tika is a project of the [Apache Software Foundation](https://www.apache.org).

Apache Tika, Tika, Apache, the Apache feather logo, and the Apache Tika project logo are trademarks of The Apache Software Foundation.

## Using Tika from AI Agents

Tika 4.x is built for agent pipelines: Markdown output by default, structured
recursive extraction (`-J` / `/rmeta`), and process isolation so a hostile
document takes down a fork, not your service.

Ready-to-use agent skills live in [`.skills/`](.skills/):
[`file-to-markdown`](.skills/users/file-to-markdown/SKILL.md) (parsing via tika-app
or tika-server) and
[`file-to-markdown-docker`](.skills/users/file-to-markdown-docker/SKILL.md)
(containerized Tika with guaranteed OCR). They are standalone — copy them into
any agent's skill directory; nothing in them requires this repository.

## Quick Start

**Parse a file in Java:**

```java
import org.apache.tika.Tika;

Tika tika = new Tika();
String text = tika.parseToString(new File("document.pdf"));
System.out.println(text);
```

**From the command line** — unzip `tika-app-<version>.zip` into a directory (the zip
has no top-level directory of its own) and run from inside it. The jar is a thin
launcher that loads the parsers from the adjacent `lib/`; on its own it fails with
`NoClassDefFoundError`.

```bash
java -jar tika-app-<version>.jar document.pdf        # Markdown (the 4.x default)
java -jar tika-app-<version>.jar --text document.pdf # plain text
java -jar tika-app-<version>.jar -J document.pdf     # structured JSON: metadata +
                                                     # content for the document AND
                                                     # anything embedded in it
```

**Maven dependency:**

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>4.x.y</version>
    <type>pom</type>
</dependency>
```

## Getting Started

Pre-built binaries of the Apache Tika standalone applications are available from
<https://tika.apache.org/download.html>. Pre-built binaries of all the Tika jars can be
fetched from Maven Central or your favourite Maven mirror.

Tika 2.x and support for Java 8 reached End of Life (EOL) in April 2025. See the
[Tika Roadmap](https://cwiki.apache.org/confluence/display/TIKA/Tika+Roadmap+--+2.x%2C+3.x+and+Beyond)
for the support schedule of each line.

## Building from Source

Tika is based on **Java 17** and uses the [Maven 3](https://maven.apache.org) build system.
The Maven wrapper (`mvnw`) is included in the repository and downloads the correct Maven
version if needed; on Windows, use `mvnw.cmd` instead.

**N.B.** [Docker](https://www.docker.com/products/personal) is used for tests in
tika-integration-tests. If Docker is not installed, those tests are skipped.

Build everything from the main directory:

    ./mvnw clean install

That produces a runnable `tika-app` you can use to try out Tika features:

    java -jar tika-app/target/tika-app-*.jar --help

To build a single project and its dependencies (for example, tika-server-standard):

    ./mvnw clean install -am -pl :tika-server-standard

If the ossindex-maven-plugin fails the build because a dependency has since been
discovered to have a vulnerability:

    ./mvnw clean install -Dossindex.skip

### Faster Builds

* **Fast profile** — `-Pfast` skips tests, checkstyle, and spotless.
* **Parallel builds** — `-T1C` builds with one thread per CPU core.
* **[Maven Daemon](https://github.com/apache/maven-mvnd) (`mvnd`)** — keeps a warm JVM
  running for 2-3x faster rebuilds and is otherwise a drop-in for `mvn`. On macOS:
  `brew install mvndaemon/tap/mvnd`.

Combine them for maximum speed during development:

    mvnd clean install -Pfast -T1C

### Building a Specific Tag

To build, say, the 3.0.1 tag:

```bash
git clone https://github.com/apache/tika.git
cd tika
git checkout 3.0.1
./mvnw clean install
```

If a new vulnerability has been discovered between the date of the tag and the date you
are building it, add `-Dossindex.skip`.

If a local test does not work in your environment, please notify the project at
dev@tika.apache.org. As an immediate workaround, you can turn off individual tests:

    ./mvnw clean install -Dtest=\!UnpackerResourceTest#testPDFImages

### Reproducible Builds

Apache Tika supports [reproducible builds](https://reproducible-builds.org/): building the
same source code with the same JDK version produces byte-for-byte identical artifacts,
regardless of the build machine or time. `project.build.outputTimestamp` is set in
`tika-parent/pom.xml`, and all Maven plugins are configured to produce deterministic output.

To verify the build plan supports reproducibility:

    ./mvnw artifact:check-buildplan

To verify two builds produce identical artifacts:

    ./mvnw clean install -DskipTests
    mv ~/.m2/repository/org/apache/tika tika-build-1
    ./mvnw clean install -DskipTests
    diff -r tika-build-1 ~/.m2/repository/org/apache/tika

## Maven Dependencies

Apache Tika provides a *Bill of Materials* (BOM) artifact that aligns Tika module versions.
Import it (or Tika's parent pom.xml) in your dependency management section to avoid
convergence errors in your own project.

If you use Apache Maven:

```xml
<project>
  <dependencyManagement>
    <dependencies>
      <dependency>
       <groupId>org.apache.tika</groupId>
       <artifactId>tika-bom</artifactId>
       <version>4.x.y</version>
       <type>pom</type>
       <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.apache.tika</groupId>
      <artifactId>tika-parsers-standard-package</artifactId>
      <!-- version not required since BOM included -->
      <type>pom</type>
    </dependency>
  </dependencies>
</project>
```

For Gradle:

```kotlin
dependencies {
  implementation(platform("org.apache.tika:tika-bom:4.x.y"))

  // version not required since bom (platform in Gradle terms)
  implementation("org.apache.tika:tika-parsers-standard-package@pom")
}
```

## Migrating to 4.x

Upgrading from 3.x requires code and configuration changes: Java 17, `TikaInputStream` in
the `Parser`/`Detector` SPI, JSON configuration instead of `tika-config.xml`, namespaced
metadata keys, and Markdown as the default output format. Start with
[Migrating to Tika 4.x](docs/modules/ROOT/pages/migration-to-4x/migrating-to-4x.adoc);
tika-server users should also read
[Migrating Tika Server to 4.x](docs/modules/ROOT/pages/migration-to-4x/migrating-tika-server-4x.adoc).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and <https://tika.apache.org/contribute.html>.

[![contributors](https://contributors-img.web.app/image?repo=apache/tika)](https://github.com/apache/tika/graphs/contributors)

## Mailing Lists

* user@tika.apache.org - About using Tika
* dev@tika.apache.org - About developing Tika

Subscribe by sending a message to `{list}-subscribe@tika.apache.org`.

## Issue Tracker

<https://issues.apache.org/jira/browse/TIKA>

## Security

See [SECURITY.md](SECURITY.md) and <https://tika.apache.org/security.html>.

## License (see also LICENSE.txt)

Collective work: Copyright 2011 The Apache Software Foundation.

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at

<https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.

Apache Tika includes a number of subcomponents with separate copyright notices and license terms. Your use of these subcomponents is subject to the terms and conditions of the licenses listed in the LICENSE.txt file.

## Export Control

This distribution includes cryptographic software.  The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.  BEFORE using any encryption software, please  check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to  see if this is permitted.  See <http://www.wassenaar.org/> for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms.  The form and manner of this Apache Software Foundation distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

The following provides more details on the included cryptographic software:

Apache Tika uses the Bouncy Castle generic encryption libraries for extracting text content and metadata from encrypted PDF files.  See <http://www.bouncycastle.org/> for more details on Bouncy Castle.
