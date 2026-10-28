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
package org.apache.tika.pipes.grpc.compose;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.apache.tika.pipes.grpc.compose.records.Header;
import org.apache.tika.pipes.grpc.compose.records.ListRecordsRequest;
import org.apache.tika.pipes.grpc.compose.records.ListRecordsResponse;
import org.apache.tika.pipes.grpc.compose.records.Record;
import org.apache.tika.pipes.grpc.compose.records.RecordError;
import org.apache.tika.pipes.grpc.compose.records.RecordListServiceGrpc;

/**
 * The parser behind {@link RecordListAdapter}, in process: it reads the WARC it is sent
 * (gunzipping first) and answers with one record per WARC record, with the WARC headers, the
 * record date, and the HTTP status line and headers of response records. Bytes that are not
 * a WARC get an unrecoverable error.
 */
public final class FakeRecordList extends RecordListServiceGrpc.RecordListServiceImplBase {

    private static final String RESPONSE_BODY = "<html><title>hi</title></html>";

    /** A warcinfo record and an HTTP response record for https://example.com/. */
    public static final ByteString SAMPLE = ByteString.copyFrom(String.join("\r\n",
            "WARC/1.1",
            "WARC-Type: warcinfo",
            "WARC-Record-ID: <urn:uuid:00000000-0000-0000-0000-000000000001>",
            "WARC-Date: 2026-10-28T08:00:00Z",
            "Content-Type: application/warc-fields",
            "Content-Length: 18",
            "",
            "software: test\r\n\r\n",
            "WARC/1.1",
            "WARC-Type: response",
            "WARC-Record-ID: <urn:uuid:00000000-0000-0000-0000-000000000002>",
            "WARC-Date: 2026-10-28T08:00:01Z",
            "WARC-Target-URI: https://example.com/",
            "Content-Type: application/http; msgtype=response",
            "Content-Length: " + httpBlock().length(),
            "",
            httpBlock(),
            "",
            ""), StandardCharsets.ISO_8859_1);

    private static String httpBlock() {
        return "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + RESPONSE_BODY.length() + "\r\n"
                + "\r\n"
                + RESPONSE_BODY;
    }

    public final AtomicReference<ListRecordsRequest> lastRequest = new AtomicReference<>();
    public final AtomicInteger calls = new AtomicInteger();
    public volatile Status failWith;
    /** Runs at the start of each call, on the call's thread. */
    public volatile Runnable onCall = () -> { };

    @Override
    public void listRecords(ListRecordsRequest request,
                            StreamObserver<ListRecordsResponse> observer) {
        calls.incrementAndGet();
        lastRequest.set(request);
        onCall.run();
        if (failWith != null) {
            observer.onError(failWith.asRuntimeException());
            return;
        }
        try {
            observer.onNext(parse(request.getArchive()));
            observer.onCompleted();
        } catch (IOException e) {
            observer.onError(Status.INTERNAL.withDescription(e.toString()).asRuntimeException());
        }
    }

    /** The answer for {@code archive}. */
    public static ListRecordsResponse parse(ByteString archive) throws IOException {
        InputStream raw = archive.newInput();
        if (archive.size() > 1 && (archive.byteAt(0) & 0xff) == 0x1f
                && (archive.byteAt(1) & 0xff) == 0x8b) {
            raw = new GZIPInputStream(raw);
        }
        ListRecordsResponse.Builder reply = ListRecordsResponse.newBuilder();
        try (CountingInput in = new CountingInput(new BufferedInputStream(raw))) {
            while (true) {
                long position = in.position;
                String version = in.line();
                while (version != null && version.isEmpty()) {
                    position = in.position;
                    version = in.line();
                }
                if (version == null) {
                    return reply.build();
                }
                if (!version.startsWith("WARC/")) {
                    return reply.addErrors(RecordError.newBuilder()
                            .setStreamPos(position)
                            .setRecoverable(false)
                            .setMessage("not a WARC record: " + version)).build();
                }
                reply.addRecords(record(in, position));
            }
        }
    }

    private static Record record(CountingInput in, long position) throws IOException {
        Record.Builder record = Record.newBuilder().setStreamPos(position);
        headers(in, record::addWarcHeaders);
        long length = Long.parseLong(header(record.getWarcHeadersList(), "Content-Length"));
        byte[] block = in.bytes(length);
        String date = header(record.getWarcHeadersList(), "WARC-Date");
        if (date != null) {
            Instant instant = Instant.parse(date);
            record.setDate(Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano()));
        }
        String contentType = header(record.getWarcHeadersList(), "Content-Type");
        if ("response".equals(header(record.getWarcHeadersList(), "WARC-Type"))
                && contentType != null && contentType.startsWith("application/http")) {
            try (CountingInput http = new CountingInput(
                    new BufferedInputStream(new ByteArrayInputStream(block)))) {
                record.setHttpStatusLine(http.line());
                headers(http, record::addHttpHeaders);
            }
        }
        return record.build();
    }

    private static void headers(CountingInput in, Consumer<Header> add) throws IOException {
        String line;
        while ((line = in.line()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                add.accept(Header.newBuilder()
                        .setName(line.substring(0, colon).trim())
                        .setValue(line.substring(colon + 1).trim())
                        .build());
            }
        }
    }

    private static String header(List<Header> headers, String name) {
        for (Header header : headers) {
            if (header.getName().equalsIgnoreCase(name)) {
                return header.getValue();
            }
        }
        return null;
    }

    /** Reads CRLF lines and exact byte counts, keeping the offset. */
    private static final class CountingInput implements AutoCloseable {
        private final InputStream in;
        long position;

        CountingInput(InputStream in) {
            this.in = in;
        }

        String line() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                position++;
                if (b == '\n') {
                    break;
                }
                line.write(b);
            }
            if (b == -1 && line.size() == 0) {
                return null;
            }
            String text = line.toString(StandardCharsets.ISO_8859_1);
            return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
        }

        byte[] bytes(long length) throws IOException {
            byte[] bytes = in.readNBytes((int) length);
            if (bytes.length < length) {
                throw new EOFException("record cut short");
            }
            position += length;
            return bytes;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
