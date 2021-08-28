package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base object for FSSHTTPB.
 */
public abstract class BasicObject implements IFSSHTTPBSerializable {
    /**
     * Used to parse byte array to special object.
     */
    /// <typeparam name="T">The type of target object.</typeparam>
    /// <param name="byteArray">The byte array contains raw data.</param>
    /// <param name="index">The index special where to start.</param>
    /// <returns>The instance of target object.</returns>
    public static <T extends BasicObject> T parse(byte[] byteArray, AtomicInteger index, Class<T> clazz) {
        try {
            T fsshttpbObject = clazz.newInstance();
            index.addAndGet(fsshttpbObject.DeserializeFromByteArray(byteArray, index.get()));

            return fsshttpbObject;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Could not parse basic object", e);
        }
    }

    /// <summary>
    /// Used to return the length of this element.
    /// </summary>
    /// <param name="byteArray">The byte list.</param>
    /// <param name="startIndex">The start position.</param>
    /// <returns>The element length.</returns>
    public int DeserializeFromByteArray(byte[] byteArray, int startIndex) {
        int length = this.DoDeserializeFromByteArray(byteArray, startIndex);

//        // Invoke the basic object related capture code.
//        if (SharedContext.Current.IsMsFsshttpRequirementsCaptured) {
//            new MsfsshttpbAdapterCapture().InvokeCaptureMethod(this.GetType(), this, SharedContext.Current.Site);
//        }

        return length;
    }

    /// <summary>
    /// Used to serialize item to byte list.
    /// </summary>
    /// <returns>The byte list.</returns>
    public abstract List<Byte> SerializeToByteList();

    /// <summary>
    /// Used to return the length of this element.
    /// </summary>
    /// <param name="byteArray">The byte list.</param>
    /// <param name="startIndex">The start position.</param>
    /// <returns>The element length</returns>
    protected abstract int DoDeserializeFromByteArray(byte[] byteArray, int startIndex);
}
