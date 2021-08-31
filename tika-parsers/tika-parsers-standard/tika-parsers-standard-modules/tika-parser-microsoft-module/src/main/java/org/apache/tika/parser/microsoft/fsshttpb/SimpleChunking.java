package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;

public class SimpleChunking extends AbstractChunking {
    /// <summary>
    /// Initializes a new instance of the SimpleChunking class
    /// </summary>
    /// <param name="fileContent">The content of the file.</param>
    public SimpleChunking(byte[] fileContent) {
        super(fileContent);
    }

    /// <summary>
    /// This method is used to chunk the file data.
    /// </summary>
    /// <returns>A list of LeafNodeObjectData.</returns>
    @Override
    public List<LeafNodeObject> Chunking() {
        int maxChunkSize = 1 * 1024 * 1024;
        java.util.List<LeafNodeObject> list = new ArrayList<>();
        LeafNodeObject.IntermediateNodeObjectBuilder builder = new LeafNodeObject.IntermediateNodeObjectBuilder();
        int chunkStart = 0;

        if (this.FileContent.length <= maxChunkSize) {
            list.add(builder.Build(this.FileContent, this.GetSignature(this.FileContent)));

            return list;
        }

        while (chunkStart < this.FileContent.length) {
            int chunkLength =
                    chunkStart + maxChunkSize >= this.FileContent.length ? this.FileContent.length - chunkStart :
                            maxChunkSize;
            byte[] temp = Arrays.copyOfRange(this.FileContent, chunkStart, chunkLength);
            list.add(builder.Build(temp, this.GetSignature(temp)));
            chunkStart += chunkLength;
        }

        return list;
    }

    /// <summary>
    /// Get signature for the chunk.
    /// </summary>
    /// <param name="array">The data of the chunk.</param>
    /// <returns>The signature instance.</returns>
    private SignatureObject GetSignature(byte[] array) {
        if (this.FileContent.length <= 250 * 1024 * 1024) {
            byte[] temp = DigestUtils.sha1(array);

            SignatureObject signature = new SignatureObject();
            signature.SignatureData = new BinaryItem(ByteUtil.toListOfByte(temp));
            return signature;
        } else {
            throw new RuntimeException(
                    "When the file size is larger than 250MB, the signature method is not implemented.");
        }
    }
}