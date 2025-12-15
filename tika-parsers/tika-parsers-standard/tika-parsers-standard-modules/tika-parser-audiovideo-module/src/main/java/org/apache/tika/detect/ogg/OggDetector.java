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
package org.apache.tika.detect.ogg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.OggPacket;
import org.gagravarr.ogg.OggPacketReader;
import org.gagravarr.ogg.OggStreamIdentifier;
import org.gagravarr.ogg.OggStreamIdentifier.OggStreamType;
import org.gagravarr.skeleton.SkeletonPacket;
import org.gagravarr.skeleton.SkeletonPacketFactory;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Detector for identifying specific file types stored
 * within an Ogg container.
 * Xiph provide a fairly unhelpful guide to mimetypes at
 * https://wiki.xiph.org/index.php/MIME_Types_and_File_Extensions
 * but we try to use more specific ones, as given by the Tika
 * mimetypes xml file.
 */
@TikaComponent
public class OggDetector implements Detector {
    private static final long serialVersionUID = 591382028699008553L;

    public static final MediaType OGG_GENERAL = MediaType.application("ogg");
    public static final MediaType OGG_AUDIO = MediaType.audio("ogg");
    public static final MediaType OGG_VIDEO = MediaType.video("ogg");

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata)
            throws IOException {
        // Check if we have access to the document
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }

        // Check if the document starts with the OGG header
        tis.mark(4);
        try {
            if (tis.read() != (byte)'O' || tis.read() != (byte)'g'
                    || tis.read() != (byte)'g' || tis.read() != (byte)'S') {
                return MediaType.OCTET_STREAM;
            }
        } finally {
            tis.reset();
        }

        // We could potentially need to go a long way through the
        // file in order to figure out what it is
        tis.mark((int)tis.getLength() + 1);

        try {
            // Open the Ogg file - underlying stream stays open as detecting only
            @SuppressWarnings("resource")
            OggFile ogg = new OggFile(tis);

            // The things we'll want to keep track of
            int totalStreams = 0;
            List<Integer> sids = new ArrayList<>();
            Map<OggStreamType, Integer> streams = new HashMap<>();
            Map<Integer, List<SkeletonPacket>> skeletonStreams = new HashMap<>();

            // Check the streams in turn
            OggPacketReader r = ogg.getPacketReader();
            OggPacket p;
            Integer sid;
            try {
                while ((p = r.getNextPacket()) != null) {
                    if (p.isBeginningOfStream()) {
                        totalStreams++;
                        sids.add(p.getSid());

                        OggStreamType type = OggStreamIdentifier.identifyType(p);

                        // If it's a Skeleton stream, start tracking
                        if (type == OggStreamIdentifier.SKELETON) {
                            List<SkeletonPacket> sp = new ArrayList<>();
                            sp.add(SkeletonPacketFactory.create(p));
                            skeletonStreams.put(p.getSid(), sp);
                        }

                        // Increment the per-type count
                        Integer num = streams.get(type);
                        if (num == null) {
                            num = 1;
                        } else {
                            num = num + 1;
                        }
                        streams.put(type, num);
                    } else {
                        sid = p.getSid();

                        // Is it a skeleton stream?
                        if (skeletonStreams.containsKey(sid)) {
                            skeletonStreams.get(sid).add(SkeletonPacketFactory.create(p));
                        }
                    }
                }
            } catch (UnsupportedOperationException e) {
                // Silently swallow this problem with the file,
                // and just say "not ours"
                return MediaType.OCTET_STREAM;
            }

            // Can we identify what it really is?
            // First up, is it a simple single stream file?
            if (totalStreams == 1) {
                OggStreamType type = streams.keySet().iterator().next();
                return toMediaType(type);
            }

            // Is it one with a single non-metadata stream?
            int nonMetadataStreams = 0;
            for (OggStreamType type : streams.keySet()) {
                if (type.kind != OggStreamType.Kind.METADATA) {
                    nonMetadataStreams += streams.get(type);
                }
            }

            if (nonMetadataStreams == 0) {
                // Pure metadata, report as general ogg
                return OGG_GENERAL;
            }

            if (nonMetadataStreams == 1) {
                // Report as the non metadata kind
                for (OggStreamType type : streams.keySet()) {
                    if (type.kind != OggStreamType.Kind.METADATA) {
                        return toMediaType(type);
                    }
                }
            }

            // Is it a single video stream, with zero or more audio streams?
            int videoCount = 0;
            int audioCount = 0;
            int kateCount = 0;
            Set<OggStreamType> audioTypes = new HashSet<>();
            Set<OggStreamType> videoTypes = new HashSet<>();
            for (OggStreamType type : streams.keySet()) {
                if (type.kind == OggStreamType.Kind.VIDEO) {
                    videoCount += streams.get(type);
                    videoTypes.add(type);
                } else if (type.kind == OggStreamType.Kind.AUDIO) {
                    audioCount += streams.get(type);
                    audioTypes.add(type);
                } else if (type == OggStreamIdentifier.KATE) {
                    kateCount++;
                }
            }
            if (videoCount == 1) {
                // Report it as the video type, not the audio within that
                return toMediaType(videoTypes.iterator().next());
            }

            // Is it multiple audio streams, with no video?
            if (videoCount == 0 && audioCount > 1) {
                // Are they all the same audio kind?
                if (audioTypes.size() == 1) {
                    // All the same kind, report it as that
                    return toMediaType(audioTypes.iterator().next());
                } else {
                    // Mixture of audio types, report as general audio
                    return OGG_AUDIO;
                }
            }

            // Is it multiple video streams?
            if (videoCount > 1) {
                // Are they all the same video kind?
                if (videoTypes.size() == 1) {
                    // All the same kind, report it as that video
                    return toMediaType(videoTypes.iterator().next());
                } else {
                    // Mixture of video types, report as general video
                    return OGG_VIDEO;
                }
            }

            // Is it only Kate streams, but no video nor audio?
            if (kateCount > 0) {
                return toMediaType(OggStreamIdentifier.KATE);
            }

            // If we get here, then we can't work out what it is
        } finally {
            // Tidy up - reset the stream
            tis.reset();
        }

        // Couldn't determine a more specific type
        return OGG_GENERAL;
    }

    /**
     * Converts from vorbis-java type to Tika's type
     */
    protected static MediaType toMediaType(OggStreamType type) {
        if (type == OggStreamIdentifier.UNKNOWN) {
            // We don't have a specific type available to return
            return OGG_GENERAL;
        } else {
            // Say it's the specific type we found
            return MediaType.parse(type.mimetype);
        }
    }
}
