# Tika Pipe Iterators

Tika Pipes has the ability to query for the FetchAndParseRequests from a variety of different input sources.

Tika Pipes reads FetchAndParseRequest from these input sources using the Pipe Iterators library.

This does not download each file content itself, but rather it loads batches of `org.apache.tika.FetchAndParseRequest` objects.

See the proto file here for the FetchAndParseRequest object: [tika.proto](..%2Ftika-pipes-proto%2Fsrc%2Fmain%2Fproto%2Ftika.proto)


