package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CellIDArray extends BasicObject {
    /// <summary>
    /// Initializes a new instance of the CellIDArray class.
    /// </summary>
    /// <param name="count">Specify the number of CellID in the CellID array.</param>
    /// <param name="content">Specify the list of CellID.</param>
    public CellIDArray(long count, java.util.List<CellID> content) {
        this.Count = count;
        this.Content = content;
    }

    /// <summary>
    /// Initializes a new instance of the CellIDArray class, this is copy constructor.
    /// </summary>
    /// <param name="cellIdArray">Specify the CellIDArray.</param>
    public CellIDArray(CellIDArray cellIdArray) {
        this.Count = cellIdArray.Count;
        if (cellIdArray.Content != null) {
            for (CellID cellId : cellIdArray.Content) {
                this.Content.add(new CellID(cellId));
            }
        }
    }

    /// <summary>
    /// Initializes a new instance of the CellIDArray class, this is default constructor.
    /// </summary>
    public CellIDArray() {
        this.Content = new ArrayList<CellID>();
    }

    /// <summary>
    /// Gets or sets a compact unsigned 64-bit integer that specifies the count of cell IDs in the array.
    /// </summary>
    public long Count;

    /// <summary>
    /// Gets or sets a cell ID list that specifies a list of cells.
    /// </summary>
    public List<CellID> Content;

    /// <summary>
    /// This method is used to convert the element of CellIDArray basic object into a byte List.
    /// </summary>
    /// <returns>Return the byte list which store the byte information of CellIDArray.</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<Byte>();
        byteList.addAll((new Compact64bitInt(this.Count)).SerializeToByteList());
        if (this.Content != null) {
            for (CellID extendGuid : this.Content) {
                byteList.addAll(extendGuid.SerializeToByteList());
            }
        }

        return byteList;
    }

    /// <summary>
    /// This method is used to deserialize the CellIDArray basic object from the specified byte array and start index.
    /// </summary>
    /// <param name="byteArray">Specify the byte array.</param>
    /// <param name="startIndex">Specify the start index from the byte array.</param>
    /// <returns>Return the length in byte of the CellIDArray basic object.</returns>
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.Count = BasicObject.parse(byteArray, index, Compact64bitInt.class).getDecodedValue();

        for (long i = 0; i < this.Count; i++) {
            this.Content.add(BasicObject.parse(byteArray, index, CellID.class));
        }

        return index.get() - startIndex;
    }
}