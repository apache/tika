# Apache Tika Server

https://cwiki.apache.org/confluence/display/TIKA/TikaServer

OpenAPI
-------
The OpenAPI specification can be found in `openapi.yaml` file in this directory. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082).

Running
-------
```
$ java -jar tika-server/target/tika-server.jar --help
   usage: tikaserver
    -?,--help           this help message
    -h,--host <arg>     host name (default = localhost)
    -l,--log <arg>      request URI log level ('debug' or 'info')
    -p,--port <arg>     listen port (default = 9998)
    -s,--includeStack   whether or not to return a stack trace
                        if there is an exception during 'parse'
```

Running via Docker
------------------
Assuming you have Docker installed, you can use a prebuilt image:

`docker run -d -p 9998:9998 apache/tika`

This will load Apache Tika Server and expose its interface on:

`http://localhost:9998`

You may also be interested in the https://github.com/apache/tika-docker project
which provides prebuilt Docker images.

Installing as a Service on Linux
-----------------------
To run as a service on Linux you need to run the `install_tika_service.sh` script.

Assuming you have the binary distribution like `tika-server-1.24-bin.tgz`,
then you can extract the install script via:

`tar xzf tika-server-1.24-bin.tgz tika-server-1.24-bin/bin/install_tika_service.sh --strip-components=2`

and then run the installation process via:

`./install_tika_service.sh  ./tika-server-1.24-bin.tgz`


Usage
-----
Usage examples from command line with `curl` utility:

* Extract plain text:  
`curl -T price.xls http://localhost:9998/tika`

* Extract text with mime-type hint:  
`curl -v -H "Content-type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" -T document.docx http://localhost:9998/tika`

* Get all document attachments as ZIP-file:  
`curl -v -T Doc1_ole.doc http://localhost:9998/unpacker > /var/tmp/x.zip`

* Extract metadata to CSV format:  
`curl -T price.xls http://localhost:9998/meta`

* Detect media type from CSV format using file extension hint:  
`curl -X PUT -H "Content-Disposition: attachment; filename=foo.csv" --upload-file foo.csv http://localhost:9998/detect/stream`


HTTP Return Codes
-----------------
`200` - Ok  
`204` - No content (for example when we are unpacking file without attachments)  
`415` - Unknown file type  
`422` - Unparsable document of known type (password protected documents and unsupported versions like Biff5 Excel)  
`500` - Internal error  
