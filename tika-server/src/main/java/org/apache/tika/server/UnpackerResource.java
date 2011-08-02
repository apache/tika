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

package org.apache.tika.server;

import org.apache.commons.lang.mutable.MutableInt;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.IOUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.zip.ZipOutputStream;

@Path("/unpacker{id:(/.*)?}")
public class UnpackerResource {
  private static final Log logger = LogFactory.getLog(UnpackerResource.class);

  private final TikaConfig tikaConfig;

  public UnpackerResource() {
    tikaConfig = TikaConfig.getDefaultConfig();
  }

  @PUT
  @Produces("application/zip")
  public StreamingOutput getText(
          InputStream is,
          @Context HttpHeaders httpHeaders,
          @Context UriInfo info
  ) throws Exception {
    Metadata metadata = new Metadata();

    AutoDetectParser parser = TikaResource.createParser();

    TikaResource.fillMetadata(parser, metadata, httpHeaders);
    TikaResource.logRequest(logger, info, metadata);

    ContentHandler ch = new DefaultHandler();

    ParseContext pc = new ParseContext();

    ZipOutput zout = new ZipOutput();
    MutableInt count = new MutableInt();

    pc.set(EmbeddedDocumentExtractor.class, new MyEmbeddedDocumentExtractor(count, zout));

    try {
      parser.parse(is, ch, metadata, pc);
    } catch (TikaException ex) {
      logger.warn(String.format(
              "%s: Unpacker failed",
              info.getPath()
      ), ex);
    }

    if (count.intValue() == 0) {
      throw new WebApplicationException(Response.Status.NO_CONTENT);
    }

    return zout;
  }

  private class MyEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
    private final MutableInt count;
    private final ZipOutput zout;

    MyEmbeddedDocumentExtractor(MutableInt count, ZipOutput zout) {
      this.count = count;
      this.zout = zout;
    }

    public boolean shouldParseEmbedded(Metadata metadata) {
      return true;
    }

    public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean b) throws SAXException, IOException {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      IOUtils.copy(inputStream, bos);
      byte[] data = bos.toByteArray();

      String name = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
      String contentType = metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);

      if (name == null) {
        name = Integer.toString(count.intValue());
      }

      if (!name.contains(".")) {
        try {
          String ext = tikaConfig.getMimeRepository().forName(contentType).getExtension();

          if (ext!=null) {
            name += ext;
          }
        } catch (MimeTypeException e) {
          logger.warn("Unexpected MimeTypeException", e);
        }
      }

      if ("application/vnd.openxmlformats-officedocument.oleObject".equals(contentType)) {
        POIFSFileSystem poifs = new POIFSFileSystem(new ByteArrayInputStream(data));
        OfficeParser.POIFSDocumentType type = OfficeParser.POIFSDocumentType.detectType(poifs);

        if (type == OfficeParser.POIFSDocumentType.OLE10_NATIVE) {
          try {
            Ole10Native ole = Ole10Native.createFromEmbeddedOleObject(poifs);
            if (ole.getDataSize()>0) {
              String label = ole.getLabel();

              if (label.startsWith("ole-")) {
                label = Integer.toString(count.intValue()) + '-' + label;
              }

              name = label;

              data = ole.getDataBuffer();
            }
          } catch (Ole10NativeException ex) {
            logger.warn("Skipping invalid part", ex);
          }
        } else {
          name += '.' + type.getExtension();
        }
      }      

      final String finalName = name;

      zout.put(new PartExtractor<byte[]>() {
        public void extract(byte[] part, ZipOutputStream output) throws IOException {
          ZipUtils.zipStoreBuffer(output, finalName, part);
        }
      }, Collections.singletonList(data));

      count.increment();
    }
  }
}
