package org.apache.tika.parser.mp4;

import java.io.IOException;

import com.drew.imaging.mp4.Mp4Handler;
import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4BoxHandler;
import com.drew.metadata.mp4.Mp4Context;
import com.drew.metadata.mp4.boxes.Box;
import org.xml.sax.SAXException;

import org.apache.tika.parser.mp4.boxes.TikaUserDataBox;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaMp4BoxHandler extends Mp4BoxHandler {

    org.apache.tika.metadata.Metadata tikaMetadata;
    final XHTMLContentHandler xhtml;
    public TikaMp4BoxHandler(Metadata metadata, org.apache.tika.metadata.Metadata tikaMetadata,
                             XHTMLContentHandler xhtml) {
        super(metadata);
        this.tikaMetadata = tikaMetadata;
        this.xhtml = xhtml;
    }

    @Override
    public boolean shouldAcceptBox(@NotNull Box box) {
        if (box.type.equals("udta")) {
            return true;
        }
        return super.shouldAcceptBox(box);
    }

    @Override
    public boolean shouldAcceptContainer(@NotNull Box box) {
        return super.shouldAcceptContainer(box);
    }

    @Override
    public Mp4Handler<?> processBox(@NotNull Box box, @Nullable byte[] payload, Mp4Context context)
            throws IOException {
        if (box.type.equals("udta")) {
            return processUserData(box, payload, context);
        }

        return super.processBox(box, payload, context);
    }


    private Mp4Handler<?> processUserData(Box box, byte[] payload, Mp4Context context) throws IOException {
        if (payload == null) {
            return this;
        }
        try {
            new TikaUserDataBox(box, payload, tikaMetadata, xhtml).addMetadata(directory);
        } catch (SAXException e) {
            throw new IOException(e);
        }
        return this;
    }
}
