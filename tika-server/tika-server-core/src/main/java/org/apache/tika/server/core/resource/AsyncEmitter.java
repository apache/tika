package org.apache.tika.server.core.resource;

import org.apache.cxf.jaxrs.impl.UriInfoImpl;
import org.apache.cxf.message.MessageImpl;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MultivaluedHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Worker thread that takes ASyncRequests off the queue and
 * processes them.
 */
public class AsyncEmitter implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncEmitter.class);


    private long maxCacheSizeBytes = 10_000_000;

    private final ArrayBlockingQueue<AsyncRequest> queue;

    public AsyncEmitter(ArrayBlockingQueue<AsyncRequest> queue) {
        this.queue = queue;
    }

    @Override
    public Integer call() throws Exception {
        while (true) {
            AsyncRequest request = queue.poll(1, TimeUnit.MINUTES);
            if (request != null) {
                processTuple(request);
            } else {
                LOG.trace("Nothing on the async queue");
            }
        }
    }

    private void processTuple(AsyncRequest request) {
        LOG.debug("Starting request id ({}) of size ({})",
                request.getId(), request.getTuples().size());
        List<EmitData> cachedEmitData = new ArrayList<>();
        Emitter emitter = TikaResource.getConfig()
                .getEmitterManager()
                .getEmitter(
                    request.getTuples().get(0).getEmitKey().getEmitterName());
        long currSize = 0;
        for (FetchEmitTuple t : request.getTuples()) {
            EmitData emitData = processTuple(t);
            long estimated = AbstractEmitter.estimateSizeInBytes(
                    emitData.getEmitKey().getKey(), emitData.getMetadataList());
            if (estimated + currSize > maxCacheSizeBytes) {
                tryToEmit(emitter, cachedEmitData, request);
                cachedEmitData.clear();
            }
            cachedEmitData.add(emitData);
            currSize += estimated;
        }
        tryToEmit(emitter, cachedEmitData, request);
        cachedEmitData.clear();
        LOG.debug("Completed request id ({})",
                request.getId(), request.getTuples().size());
    }

    private void tryToEmit(Emitter emitter, List<EmitData> cachedEmitData,
                           AsyncRequest request) {
        try {
            emitter.emit(cachedEmitData);
        } catch (IOException|TikaEmitterException e) {
            LOG.warn("async id ({}) emitter class ({}): {}",
                    request.getId(), emitter.getClass(),
                    ExceptionUtils.getStackTrace(e));
        }
    }

    private EmitData processTuple(FetchEmitTuple t) {
        Metadata userMetadata = t.getMetadata();
        Metadata metadata = new Metadata();
        String fetcherName = t.getFetchKey().getFetcherName();
        String fetchKey = t.getFetchKey().getKey();
        List<Metadata> metadataList = null;
        try (InputStream stream =
                     TikaResource.getConfig().getFetcherManager()
                             .getFetcher(fetcherName).fetch(fetchKey, metadata)) {

            metadataList = RecursiveMetadataResource.parseMetadata(
                    stream,
                    metadata,
                    new MultivaluedHashMap<>(),
                    new UriInfoImpl(new MessageImpl()), "text");
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn(t.toString(), e);
        }
        injectUserMetadata(userMetadata, metadataList);
        return new EmitData(t.getEmitKey(), metadataList);
    }

    private void injectUserMetadata(Metadata userMetadata, List<Metadata> metadataList) {
        for (String n : userMetadata.names()) {
            //overwrite whatever was there
            metadataList.get(0).set(n, null);
            for (String val : userMetadata.getValues(n)) {
                metadataList.get(0).add(n, val);
            }
        }
    }
}
