package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CellID extends BasicObject {
    /// <summary>
/// Initializes a new instance of the CellID class with specified ExGuids.
/// </summary>
/// <param name="extendGuid1">Specify the first ExGuid.</param>
/// <param name="extendGuid2">Specify the second ExGuid.</param>
    public CellID(ExGuid extendGuid1, ExGuid extendGuid2) {
        this.ExtendGUID1 = extendGuid1;
        this.ExtendGUID2 = extendGuid2;
    }

    /// <summary>
/// Initializes a new instance of the CellID class, this is the copy constructor.
/// </summary>
/// <param name="cellId">Specify the CellID.</param>
    public CellID(CellID cellId) {
        if (cellId.ExtendGUID1 != null) {
            this.ExtendGUID1 = new ExGuid(cellId.ExtendGUID1);
        }

        if (cellId.ExtendGUID2 != null) {
            this.ExtendGUID2 = new ExGuid(cellId.ExtendGUID2);
        }
    }

    /// <summary>
/// Initializes a new instance of the CellID class, this is default constructor.
/// </summary>
    public CellID() {
    }

    /// <summary>
/// Gets or sets an extended GUID that specifies the first cell identifier.
/// </summary>
    public ExGuid ExtendGUID1;

    /// <summary>
/// Gets or sets an extended GUID that specifies the second cell identifier.
/// </summary>
    public ExGuid ExtendGUID2;

    /// <summary>
/// This method is used to convert the element of CellID basic object into a byte List.
/// </summary>
/// <returns>Return the byte list which store the byte information of CellID.</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        java.util.List<Byte> byteList = new ArrayList<>();
        byteList.addAll(this.ExtendGUID1.SerializeToByteList());
        byteList.addAll(this.ExtendGUID2.SerializeToByteList());
        return byteList;
    }

    /// <summary>
/// Override the Equals method.
/// </summary>
/// <param name="obj">Specify the object.</param>
/// <returns>Return true if equals, otherwise return false.</returns>
    @Override
    public boolean equals(Object obj) {
        CellID another = (CellID) obj;

        if (another == null) {
            return false;
        }

        if (another.ExtendGUID1 != null && another.ExtendGUID2 != null && this.ExtendGUID1 != null &&
                this.ExtendGUID2 != null) {
            return another.ExtendGUID1.equals(this.ExtendGUID1) && another.ExtendGUID2.equals(this.ExtendGUID2);
        }

        return false;
    }

    /// <summary>
/// Override the GetHashCode.
/// </summary>
/// <returns>Return the hash value.</returns>
    @Override
    public int hashCode() {
        return this.ExtendGUID1.hashCode() + this.ExtendGUID2.hashCode();
    }

    /// <summary>
/// This method is used to deserialize the CellID basic object from the specified byte array and start index.
/// </summary>
/// <param name="byteArray">Specify the byte array.</param>
/// <param name="startIndex">Specify the start index from the byte array.</param>
/// <returns>Return the length in byte of the CellID basic object.</returns>
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.ExtendGUID1 = BasicObject.parse(byteArray, index, ExGuid.class);
        this.ExtendGUID2 = BasicObject.parse(byteArray, index, ExGuid.class);

        return index.get() - startIndex;
    }
}
