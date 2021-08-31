package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/**
 * FSSHTTPB Serialize interface.
 */
public interface IFSSHTTPBSerializable {

    /**
     * Serialize to byte list.
     * @return The byte list.
     */
    List<Byte> SerializeToByteList();
}
