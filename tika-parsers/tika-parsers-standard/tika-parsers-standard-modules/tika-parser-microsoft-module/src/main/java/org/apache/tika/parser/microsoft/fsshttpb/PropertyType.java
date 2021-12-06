package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.HashMap;
import java.util.Map;

public enum PropertyType {

    /// <summary>
    /// The property contains no data.
    /// </summary>
    NoData(0x1),
    /// <summary>
    /// The property is a Boolean value specified by boolValue.
    /// </summary>
    Bool(0x2),
    /// <summary>
    /// The property contains 1 byte of data in the PropertySet.rgData stream field.
    /// </summary>
    OneByteOfData(0x3),
    /// <summary>
    /// The property contains 2 bytes of data in the PropertySet.rgData stream field.
    /// </summary>
    TwoBytesOfData(0x4),
    /// <summary>
    /// The property contains 4 bytes of data in the PropertySet.rgData stream field.
    /// </summary>
    FourBytesOfData(0x5),
    /// <summary>
    /// The property contains 8 bytes of data in the PropertySet.rgData stream field.
    /// </summary>
    EightBytesOfData(0x6),
    /// <summary>
    /// The property contains a prtFourBytesOfLengthFollowedByData in the PropertySet.rgData stream field.
    /// </summary>
    FourBytesOfLengthFollowedByData(0x7),
    /// <summary>
    /// The property contains one CompactID in the ObjectSpaceObjectPropSet.OIDs.body stream field.
    /// </summary>
    ObjectID(0x8),
    /// <summary>
    /// The property contains an array of CompactID structures in the ObjectSpaceObjectPropSet.OSIDs.body stream field.
    /// </summary>
    ArrayOfObjectIDs(0x9),
    /// <summary>
    /// The property contains one CompactID structure in the ObjectSpaceObjectPropSet.OSIDs.body stream field.
    /// </summary>
    ObjectSpaceID(0xA),
    /// <summary>
    /// The property contains an array of CompactID structures in the ObjectSpaceObjectPropSet.OSIDs.body stream field.
    /// </summary>
    ArrayOfObjectSpaceIDs(0xB),
    /// <summary>
    /// The property contains one CompactID in the ObjectSpaceObjectPropSet.ContextIDs.body stream field.
    /// </summary>
    ContextID(0xC),
    /// <summary>
    /// The property contains an array of CompactID structures in the ObjectSpaceObjectPropSet.ContextIDs.body stream field.
    /// </summary>
    ArrayOfContextIDs(0xD),
    /// <summary>
    /// The property contains a prtArrayOfPropertyValues structure in the PropertySet.rgData stream field.
    /// </summary>
    ArrayOfPropertyValues(0x10),
    /// <summary>
    /// The property contains a child PropertySet structure in the PropertySet.rgData stream field of the parent PropertySet.
    /// </summary>
    PropertySet(0x11);

    private final int intVal;

    static final Map<Integer, PropertyType> valToEnumMap = new HashMap<>();

    static {
        for (PropertyType val : values()) {
            valToEnumMap.put(val.getIntVal(), val);
        }
    }

    PropertyType(int intVal) {
        this.intVal = intVal;
    }

    public int getIntVal() {
        return intVal;
    }

    public static PropertyType fromIntVal(int intVal) {
        return valToEnumMap.get(intVal);
    }

}
