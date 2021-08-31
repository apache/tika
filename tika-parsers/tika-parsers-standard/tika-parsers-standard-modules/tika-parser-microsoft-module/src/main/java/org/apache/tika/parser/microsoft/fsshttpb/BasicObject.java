package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base object for FSSHTTPB.
 */
public abstract class BasicObject implements IFSSHTTPBSerializable {
    /**
     * Used to parse byte array to special object.
     *
     * @param byteArray The byte array contains raw data.
     * @param index     The index special where to start.
     * @return The instance of target object.
     */
    public static <T extends BasicObject> T parse(byte[] byteArray, AtomicInteger index, Class<T> clazz) {
        try {
            T fsshttpbObject = clazz.newInstance();
            index.addAndGet(fsshttpbObject.DeserializeFromByteArray(byteArray, index.get()));

            return fsshttpbObject;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Could not parse basic object", e);
        }
    }

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  The byte list.
     * @param startIndex The start position.
     * @return The element length.
     */
    public int DeserializeFromByteArray(byte[] byteArray, int startIndex) {
        int length = this.DoDeserializeFromByteArray(byteArray, startIndex);

//        // Invoke the basic object related capture code.
//        if (SharedContext.Current.IsMsFsshttpRequirementsCaptured) {
//            new MsfsshttpbAdapterCapture().InvokeCaptureMethod(this.GetType(), this, SharedContext.Current.Site);
//        }

        return length;
    }

    /**
     * Used to serialize item to byte list.
     *
     * @return The byte list.
     */
    public abstract List<Byte> SerializeToByteList();

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  The byte list.
     * @param startIndex The start position.
     * @return The element length
     */
    protected abstract int DoDeserializeFromByteArray(byte[] byteArray, int startIndex);
}
