<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->
# Apache Tika Server

See the [Apache Tika documentation](https://tika.apache.org/docs) (Using Tika &gt; Tika Server) for full usage.

Running
-------
```
$ java -jar tika-server/tika-server-standard/target/tika-server-standard-<version>.jar --help
   usage: tikaserver
    -?,--help           this help message
    -c,--config <arg>   tika-config file
    -h,--host <arg>     host name (default = localhost, use * for all)
    -i,--id <arg>       id for this server, written to the startup log
    -p,--port <arg>     listen port (default = 9998)
```

Everything beyond host, port and id is configured in the tika-config JSON file
passed with `-c`, not on the command line.

Running via Docker
------------------
Assuming you have Docker installed, you can use a prebuilt image:

`docker run -d -p 127.0.0.1:9998:9998 apache/tika`

This will load Apache Tika Server and expose its interface on:

`http://localhost:9998`

Note the `127.0.0.1:` prefix. Unlike the jar, which binds `localhost` by default,
the Docker images start the server with `-h 0.0.0.0`, so publishing the port
without an explicit interface exposes it on every interface of the host.
tika-server performs no authentication and parses untrusted files; only expose it
on a trusted, access-controlled network. See the
[Tika Security Model](https://tika.apache.org/security-model.html).

You may also be interested in the https://github.com/apache/tika-docker project
which provides prebuilt Docker images.

Installing as a Service on Linux
-----------------------
To run as a service on Linux you need to run the `install_tika_service.sh` script.

Assuming you have the binary distribution `tika-server-standard-<version>.zip`,
you can extract the install script via:

`unzip -j tika-server-standard-<version>.zip bin/install_tika_service.sh`

and then run the installation process (as root) via:

`./install_tika_service.sh ./tika-server-standard-<version>.zip`


Usage
-----
Usage examples from command line with `curl` utility:

* Extract Markdown (the default output of bare `/tika`):  
`curl -T price.xls http://localhost:9998/tika`

* Extract plain text:  
`curl -T price.xls http://localhost:9998/tika/text`

* Extract Markdown with mime-type hint:  
`curl -v -H "Content-type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" -T document.docx http://localhost:9998/tika`

* Get all document attachments as ZIP-file:  
`curl -v -T Doc1_ole.doc http://localhost:9998/unpack > /var/tmp/x.zip`

* Extract metadata to CSV format:  
`curl -T price.xls http://localhost:9998/meta`

* Detect media type from CSV format using file extension hint:  
`curl -X PUT -H "Content-Disposition: attachment; filename=foo.csv" --upload-file foo.csv http://localhost:9998/detect`


HTTP Return Codes
-----------------
`200` - Ok  
`204` - No content (for example when we are unpacking file without attachments)  
`400` - Bad request (unknown or invalid handler type, or a reserved/unknown fetcher or emitter was named)  
`403` - Forbidden (per-request configuration was supplied but `allowPerRequestConfig` is off)  
`413` - Payload too large (the request body exceeds `maxRequestSizeBytes`, or a pipes payload limit was exceeded)  
`422` - Unparsable document of known type (password protected documents and unsupported versions like Biff5 Excel)  
`429` - Too many requests (all forked workers were busy for longer than `maxWaitForClientMillis`; retry with backoff)  
`500` - Internal error  
`503` - Service unavailable (the forked worker hit a timeout, ran out of memory, or crashed)  
