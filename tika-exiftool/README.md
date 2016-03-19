Overview
========

tika-exifool is an [Apache Tika](http://tika.apache.org/) external parser
which invokes the command line [ExifTool](http://www.sno.phy.queensu.ca/~phil/exiftool/)
and can map the output to specific Tika metadata fields.

An IPTC extractor and mapping to Tika's IPTC metadata is provided.


Requirements
============

ExifTool must be installed.  You can specify the full path to the command line in 
`org/apache/tika/parser/exiftool/tika.exiftool.properties` or 
`org/apache/tika/parser/exiftool/tika.exiftool.override.properties`.