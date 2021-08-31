package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/**
 * Base class of data element
 */
public abstract class DataElementData implements IFSSHTTPBSerializable {
    /**
     * De-serialize data element data from byte array.
     *
     * @param byteArray  The byte array.
     * @param startIndex The position where to start.
     * @return The length of the item.
     */
    public abstract int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex);

    /**
     * Serialize item to byte list.
     *
     * @return The byte list.
     */
    public abstract List<Byte> SerializeToByteList();
}