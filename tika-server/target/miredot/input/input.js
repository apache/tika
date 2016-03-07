var com = { qmino : { miredot : {}}};
com.qmino.miredot.restApiSource = {"validLicence":true,"allowUsageTracking":true,"licenceErrorMessage":null,"jsonDocHidden":false,"licenceHash":"5016150130730579179","miredotVersion":"1.4","baseUrl":"http:\/\/www.example.com","jsonDocEnabled":true,"dateOfGeneration":"2016-03-03 22:11:24","licenceType":"PRO","projectName":"Apache Tika server","projectVersion":"1.11","projectTitle":"Apache Tika server-1.11"};
com.qmino.miredot.restApiSource.tos = {
	org_apache_cxf_jaxrs_ext_multipart_ContentDisposition_in: { "type": "complex", "name": "org_apache_cxf_jaxrs_ext_multipart_ContentDisposition_in", "content": [] },
	org_apache_cxf_jaxrs_ext_multipart_ContentDisposition_out: { "type": "complex", "name": "org_apache_cxf_jaxrs_ext_multipart_ContentDisposition_out", "content": [] },
	org_apache_cxf_jaxrs_ext_multipart_Attachment_in: { "type": "complex", "name": "org_apache_cxf_jaxrs_ext_multipart_Attachment_in", "content": [] },
	org_apache_cxf_jaxrs_ext_multipart_Attachment_out: { "type": "complex", "name": "org_apache_cxf_jaxrs_ext_multipart_Attachment_out", "content": [] }
};

com.qmino.miredot.restApiSource.enums = {

};
com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_ContentDisposition_in"].content = [ 

];
com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_ContentDisposition_out"].content = [ 
	{
		"name": "type",
		"comment": null,
		"typeValue": { "type": "simple", "typeValue": "string" }
	},
	{
		"name": "parameters",
		"comment": null,
		"typeValue": { "type": "map", "typeValue": { "type": "simple", "typeValue": "string" } }}
];
com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_Attachment_in"].content = [ 

];
com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_Attachment_out"].content = [ 
	{
		"name": "dataHandler",
		"comment": null,
		"typeValue": { "type": "simple", "typeValue": "javax.activation.DataHandler" }
	},
	{
		"name": "headers",
		"comment": null,
		"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.MultivaluedMap<java.lang.String, java.lang.String>" }
	},
	{
		"name": "contentId",
		"comment": null,
		"typeValue": { "type": "simple", "typeValue": "string" }
	},
	{
		"name": "contentDisposition",
		"comment": null,
		"typeValue": com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_ContentDisposition_out"]
	},
	{
		"name": "contentType",
		"comment": null,
		"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.MediaType" }
	},
	{
		"name": "object",
		"comment": null,
		"typeValue": { "type": "simple", "typeValue": "object" }}
];
com.qmino.miredot.restApiSource.interfaces = [
	{
		"beschrijving": "",
		"url": "/tika/form",
		"http": "POST",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["multipart/form-data"],
		"produces": ["text/html"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.StreamingOutput" }, "comment": null},
		"statusCodes": [],
		"hash": "324743643",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_Attachment_in"], "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/tika/form",
		"http": "POST",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["multipart/form-data"],
		"produces": ["text/xml"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.StreamingOutput" }, "comment": null},
		"statusCodes": [],
		"hash": "1364245073",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_Attachment_in"], "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/meta/",
		"http": "PUT",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": [
                "text/csv",
                "application/json",
                "application/rdf+xml"
            ],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.Response" }, "comment": null},
		"statusCodes": [],
		"hash": "109454980",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/detectors/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["application/json"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "393955903",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/parsers/details",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "425943851",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/translate/all/{translator}/{src}/{dest}",
		"http": "POST",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["*/*"],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1365537257",
		"inputs": {
                "PATH": [
                    {"name": "translator", "typeValue": { "type": "simple", "typeValue": "string" }, "comment": null, "jaxrs": "PATH"},
                    {"name": "src", "typeValue": { "type": "simple", "typeValue": "string" }, "comment": null, "jaxrs": "PATH"},
                    {"name": "dest", "typeValue": { "type": "simple", "typeValue": "string" }, "comment": null, "jaxrs": "PATH"}
                ],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/meta/form",
		"http": "POST",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["multipart/form-data"],
		"produces": [
                "text/csv",
                "application/json",
                "application/rdf+xml"
            ],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.Response" }, "comment": null},
		"statusCodes": [],
		"hash": "-1391765029",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_Attachment_in"], "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/translate/all/{translator}/{dest}",
		"http": "POST",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["*/*"],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1745264595",
		"inputs": {
                "PATH": [
                    {"name": "translator", "typeValue": { "type": "simple", "typeValue": "string" }, "comment": null, "jaxrs": "PATH"},
                    {"name": "dest", "typeValue": { "type": "simple", "typeValue": "string" }, "comment": null, "jaxrs": "PATH"}
                ],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/language/stream",
		"http": "POST",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["*/*"],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "-1344596477",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/tika/form",
		"http": "POST",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["multipart/form-data"],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.StreamingOutput" }, "comment": null},
		"statusCodes": [],
		"hash": "1889919997",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_Attachment_in"], "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/unpack/{id:(/.*",
		"http": "PUT",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": [
                "application/zip",
                "application/x-tar"
            ],
		"roles": [],
		"output": {"typeValue": { "type": "map", "typeValue": { "type": "simple", "typeValue": "object" } }, "comment": null},
		"statusCodes": [],
		"hash": "-254428343",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/detect/stream",
		"http": "PUT",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["*/*"],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1775361201",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "Returns an InputStream that can be deserialized as a list of {@link Metadata} objects. The first in the list represents the main document, and the rest represent metadata for the embedded objects. This works recursively through all descendants of the main document, not just the immediate children. <p> The extracted text content is stored with the key {@link RecursiveParserWrapper#TIKA_CONTENT}. <p> Specify the handler for the content (xml, html, text, ignore) in the path:<br/> /rmeta (default: xml)<br/> /rmeta/xml (store the content as xml)<br/> /rmeta/text (store the content as text)<br/> /rmeta/ignore (don't record any content)<br/>",
		"url": "/rmeta/{handler : (\\w+",
		"http": "PUT",
		"title": "Returns an InputStream that can be deserialized as a list of {@link Metadata} objects",
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["application/json"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.Response" }, "comment": "InputStream that can be deserialized as a list of {@link Metadata} objects"},
		"statusCodes": [],
		"hash": "-371852553",
		"inputs": {
                "PATH": [{"name": "handler", "typeValue": { "type": "simple", "typeValue": "string" }, "comment": "which type of handler to use", "jaxrs": "PATH"}],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1458272393",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/mime-types/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["application/json"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "646530223",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/parsers/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/html"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1177969168",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/parsers/details",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["application/json"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1880785916",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/parsers/details",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/html"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1880727231",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/tika/",
		"http": "PUT",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["*/*"],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.StreamingOutput" }, "comment": null},
		"statusCodes": [],
		"hash": "264524322",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/tika/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "660716300",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/detectors/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/html"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "-1858338157",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/version/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "763529517",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/html"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "-368862996",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "Get a specific metadata field. If the input stream cannot be parsed, but a value was found for the given metadata field, then the value of the field is returned as part of a 200 OK response; otherwise a {@link javax.ws.rs.core.Response.Status#BAD_REQUEST} is generated. If the stream was successfully parsed but the specific metadata field was not found, then a {@link javax.ws.rs.core.Response.Status#NOT_FOUND} is returned. <p/> Note that this method handles multivalue fields and returns possibly more metadata value than requested. <p/> If you want XMP, you must be careful to specify the exact XMP key. For example, \"Author\" will return nothing, but \"dc:creator\" will return the correct value.",
		"url": "/meta/{field}",
		"http": "PUT",
		"title": "Get a specific metadata field",
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": [
                "text/csv",
                "application/json",
                "application/rdf+xml",
                "text/plain"
            ],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.Response" }, "comment": "one of {@link javax.ws.rs.core.Response.Status#OK}, {@link javax.ws.rs.core.Response.Status#NOT_FOUND}, or {@link javax.ws.rs.core.Response.Status#BAD_REQUEST}"},
		"statusCodes": [],
		"hash": "-957735333",
		"inputs": {
                "PATH": [{"name": "field", "typeValue": { "type": "simple", "typeValue": "string" }, "comment": "the tika metadata field name", "jaxrs": "PATH"}],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": "inputstream", "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/tika/",
		"http": "PUT",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["*/*"],
		"produces": ["text/xml"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.StreamingOutput" }, "comment": null},
		"statusCodes": [],
		"hash": "1296004430",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/parsers/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "-2129538075",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/language/string",
		"http": "POST",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["*/*"],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1776623824",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/tika/",
		"http": "PUT",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["*/*"],
		"produces": ["text/html"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.StreamingOutput" }, "comment": null},
		"statusCodes": [],
		"hash": "-1221374332",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/mime-types/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/html"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "646471538",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/parsers/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["application/json"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "1178027853",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/mime-types/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "-1426095421",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/unpack/all{id:(/.*",
		"http": "PUT",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": [
                "application/zip",
                "application/x-tar"
            ],
		"roles": [],
		"output": {"typeValue": { "type": "map", "typeValue": { "type": "simple", "typeValue": "object" } }, "comment": null},
		"statusCodes": [],
		"hash": "443972170",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [{"typeValue": { "type": "simple", "typeValue": "java.io.InputStream" }, "comment": null, "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "",
		"url": "/detectors/",
		"http": "GET",
		"title": null,
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": [],
		"produces": ["text/plain"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "string" }, "comment": null},
		"statusCodes": [],
		"hash": "-665964749",
		"inputs": {
                "PATH": [],
                "QUERY": [],
                "BODY": [],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	},
	{
		"beschrijving": "Returns an InputStream that can be deserialized as a list of {@link Metadata} objects. The first in the list represents the main document, and the rest represent metadata for the embedded objects. This works recursively through all descendants of the main document, not just the immediate children. <p> The extracted text content is stored with the key {@link RecursiveParserWrapper#TIKA_CONTENT}. <p> Specify the handler for the content (xml, html, text, ignore) in the path:<br/> /rmeta/form (default: xml)<br/> /rmeta/form/xml (store the content as xml)<br/> /rmeta/form/text (store the content as text)<br/> /rmeta/form/ignore (don't record any content)<br/>",
		"url": "/rmeta/form{handler : (\\w+",
		"http": "POST",
		"title": "Returns an InputStream that can be deserialized as a list of {@link Metadata} objects",
		"tags": [],
		"authors": [],
		"compressed": false,
		"deprecated": false,
		"consumes": ["multipart/form-data"],
		"produces": ["application/json"],
		"roles": [],
		"output": {"typeValue": { "type": "simple", "typeValue": "javax.ws.rs.core.Response" }, "comment": "InputStream that can be deserialized as a list of {@link Metadata} objects"},
		"statusCodes": [],
		"hash": "1062070258",
		"inputs": {
                "PATH": [{"name": "handler", "typeValue": { "type": "simple", "typeValue": "string" }, "comment": "which type of handler to use", "jaxrs": "PATH"}],
                "QUERY": [],
                "BODY": [{"typeValue": com.qmino.miredot.restApiSource.tos["org_apache_cxf_jaxrs_ext_multipart_Attachment_in"], "comment": "attachment", "jaxrs": "BODY"}],
                "HEADER": [],
                "COOKIE": [],
                "FORM": [],
                "MATRIX": []
            }
	}];
com.qmino.miredot.projectWarnings = [
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "324743643",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "324743643",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "324743643",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "324743643",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "324743643",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1364245073",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1364245073",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1364245073",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1364245073",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1364245073",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXMLFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "109454980",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "109454980",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "109454980",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "109454980",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "109454980",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadata",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "109454980",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "JAXRS_MISSING_CONSUMES",
		"description": "Interface specifies a JAXRS-BODY parameter, but does not specify a Consumes value.",
		"failedBuild": false,
		"interface": "109454980",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "109454980",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadata",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "393955903",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDetectorsJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "393955903",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDetectorsJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "393955903",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDetectorsJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "393955903",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDetectorsJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "425943851",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailssPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "425943851",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailssPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "425943851",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailssPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "425943851",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailssPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": "translator"
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": "src"
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": "dest"
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": "org.apache.tika.exception.TikaException"
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": "java.io.IOException"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": "org.apache.tika.exception.TikaException"
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "1365537257",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "translate",
		"entity": "java.io.IOException"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-1391765029",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-1391765029",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "-1391765029",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-1391765029",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "-1391765029",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-1391765029",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "-1391765029",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": "translator"
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": "dest"
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": "org.apache.tika.exception.TikaException"
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": "java.io.IOException"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": "org.apache.tika.exception.TikaException"
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "1745264595",
		"implementationClass": "org.apache.tika.server.resource.TranslateResource",
		"implementationMethod": "autoTranslate",
		"entity": "java.io.IOException"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-1344596477",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-1344596477",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "-1344596477",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-1344596477",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "-1344596477",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": "java.io.IOException"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-1344596477",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "-1344596477",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": "java.io.IOException"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1889919997",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getTextFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1889919997",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getTextFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1889919997",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getTextFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1889919997",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getTextFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1889919997",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getTextFromMultipart",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-254428343",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpack",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-254428343",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpack",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "-254428343",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpack",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-254428343",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpack",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "-254428343",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpack",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-254428343",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpack",
		"entity": null
	},
	{
		"category": "JAXRS_MISSING_CONSUMES",
		"description": "Interface specifies a JAXRS-BODY parameter, but does not specify a Consumes value.",
		"failedBuild": false,
		"interface": "-254428343",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpack",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "-254428343",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpack",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1775361201",
		"implementationClass": "org.apache.tika.server.resource.DetectorResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1775361201",
		"implementationClass": "org.apache.tika.server.resource.DetectorResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1775361201",
		"implementationClass": "org.apache.tika.server.resource.DetectorResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1775361201",
		"implementationClass": "org.apache.tika.server.resource.DetectorResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1775361201",
		"implementationClass": "org.apache.tika.server.resource.DetectorResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "-371852553",
		"implementationClass": "org.apache.tika.server.resource.RecursiveMetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "-371852553",
		"implementationClass": "org.apache.tika.server.resource.RecursiveMetadataResource",
		"implementationMethod": "getMetadata",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-371852553",
		"implementationClass": "org.apache.tika.server.resource.RecursiveMetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "JAXRS_MISSING_CONSUMES",
		"description": "Interface specifies a JAXRS-BODY parameter, but does not specify a Consumes value.",
		"failedBuild": false,
		"interface": "-371852553",
		"implementationClass": "org.apache.tika.server.resource.RecursiveMetadataResource",
		"implementationMethod": "getMetadata",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "-371852553",
		"implementationClass": "org.apache.tika.server.resource.RecursiveMetadataResource",
		"implementationMethod": "getMetadata",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1458272393",
		"implementationClass": "org.apache.tika.server.resource.TikaWelcome",
		"implementationMethod": "getWelcomePlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1458272393",
		"implementationClass": "org.apache.tika.server.resource.TikaWelcome",
		"implementationMethod": "getWelcomePlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1458272393",
		"implementationClass": "org.apache.tika.server.resource.TikaWelcome",
		"implementationMethod": "getWelcomePlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1458272393",
		"implementationClass": "org.apache.tika.server.resource.TikaWelcome",
		"implementationMethod": "getWelcomePlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "646530223",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "646530223",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "646530223",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "646530223",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1177969168",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1177969168",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1177969168",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1177969168",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1880785916",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailsJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1880785916",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailsJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1880785916",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailsJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1880785916",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailsJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1880727231",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailsHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1880727231",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailsHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1880727231",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailsHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1880727231",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParserDetailsHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "264524322",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getText",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "264524322",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getText",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "264524322",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getText",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "264524322",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getText",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "264524322",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getText",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "660716300",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getMessage",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "660716300",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getMessage",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "660716300",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getMessage",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "660716300",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getMessage",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-1858338157",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDectorsHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-1858338157",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDectorsHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-1858338157",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDectorsHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-1858338157",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDectorsHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "763529517",
		"implementationClass": "org.apache.tika.server.resource.TikaVersion",
		"implementationMethod": "getVersion",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "763529517",
		"implementationClass": "org.apache.tika.server.resource.TikaVersion",
		"implementationMethod": "getVersion",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "763529517",
		"implementationClass": "org.apache.tika.server.resource.TikaVersion",
		"implementationMethod": "getVersion",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "763529517",
		"implementationClass": "org.apache.tika.server.resource.TikaVersion",
		"implementationMethod": "getVersion",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-368862996",
		"implementationClass": "org.apache.tika.server.resource.TikaWelcome",
		"implementationMethod": "getWelcomeHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-368862996",
		"implementationClass": "org.apache.tika.server.resource.TikaWelcome",
		"implementationMethod": "getWelcomeHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-368862996",
		"implementationClass": "org.apache.tika.server.resource.TikaWelcome",
		"implementationMethod": "getWelcomeHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-368862996",
		"implementationClass": "org.apache.tika.server.resource.TikaWelcome",
		"implementationMethod": "getWelcomeHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "-957735333",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataField",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-957735333",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataField",
		"entity": null
	},
	{
		"category": "JAXRS_MISSING_CONSUMES",
		"description": "Interface specifies a JAXRS-BODY parameter, but does not specify a Consumes value.",
		"failedBuild": false,
		"interface": "-957735333",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataField",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "-957735333",
		"implementationClass": "org.apache.tika.server.resource.MetadataResource",
		"implementationMethod": "getMetadataField",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1296004430",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1296004430",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1296004430",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1296004430",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1296004430",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getXML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-2129538075",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-2129538075",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-2129538075",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-2129538075",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1776623824",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1776623824",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "1776623824",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1776623824",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "1776623824",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": "java.io.IOException"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1776623824",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "1776623824",
		"implementationClass": "org.apache.tika.server.resource.LanguageResource",
		"implementationMethod": "detect",
		"entity": "java.io.IOException"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-1221374332",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-1221374332",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "-1221374332",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-1221374332",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-1221374332",
		"implementationClass": "org.apache.tika.server.resource.TikaResource",
		"implementationMethod": "getHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "646471538",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "646471538",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "646471538",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "646471538",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesHTML",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "1178027853",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "1178027853",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "1178027853",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1178027853",
		"implementationClass": "org.apache.tika.server.resource.TikaParsers",
		"implementationMethod": "getParsersJSON",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-1426095421",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-1426095421",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-1426095421",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-1426095421",
		"implementationClass": "org.apache.tika.server.resource.TikaMimeTypes",
		"implementationMethod": "getMimeTypesPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "443972170",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpackAll",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "443972170",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpackAll",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing parameter documentation",
		"failedBuild": false,
		"interface": "443972170",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpackAll",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "443972170",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpackAll",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "443972170",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpackAll",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "443972170",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpackAll",
		"entity": null
	},
	{
		"category": "JAXRS_MISSING_CONSUMES",
		"description": "Interface specifies a JAXRS-BODY parameter, but does not specify a Consumes value.",
		"failedBuild": false,
		"interface": "443972170",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpackAll",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "443972170",
		"implementationClass": "org.apache.tika.server.resource.UnpackerResource",
		"implementationMethod": "unpackAll",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_SUMMARY",
		"description": "Missing summary tag",
		"failedBuild": false,
		"interface": "-665964749",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDetectorsPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_INTERFACEDOCUMENTATION",
		"description": "Missing interface documentation",
		"failedBuild": false,
		"interface": "-665964749",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDetectorsPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_PARAMETER_DOCUMENTATION",
		"description": "Missing return type documentation",
		"failedBuild": false,
		"interface": "-665964749",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDetectorsPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "-665964749",
		"implementationClass": "org.apache.tika.server.resource.TikaDetectors",
		"implementationMethod": "getDetectorsPlain",
		"entity": null
	},
	{
		"category": "JAVADOC_MISSING_EXCEPTION_DOCUMENTATION",
		"description": "Exception thrown by method has no comment",
		"failedBuild": false,
		"interface": "1062070258",
		"implementationClass": "org.apache.tika.server.resource.RecursiveMetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": "java.lang.Exception"
	},
	{
		"category": "JAVADOC_MISSING_AUTHORS",
		"description": "No author(s) specified for interface.",
		"failedBuild": false,
		"interface": "1062070258",
		"implementationClass": "org.apache.tika.server.resource.RecursiveMetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": null
	},
	{
		"category": "REST_UNMAPPED_EXCEPTION",
		"description": "Exception is thrown by interface specification, but is not mapped in the MireDot configuration. As such, the return errorcode can not be documented properly.",
		"failedBuild": false,
		"interface": "1062070258",
		"implementationClass": "org.apache.tika.server.resource.RecursiveMetadataResource",
		"implementationMethod": "getMetadataFromMultipart",
		"entity": "java.lang.Exception"
	}];
com.qmino.miredot.processErrors  = [
];

