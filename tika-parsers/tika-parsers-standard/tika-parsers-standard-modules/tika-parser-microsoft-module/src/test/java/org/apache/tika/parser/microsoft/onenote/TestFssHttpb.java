package org.apache.tika.parser.microsoft.onenote;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.apache.tika.parser.microsoft.fsshttpb.DataElement;
import org.apache.tika.parser.microsoft.fsshttpb.DataElementUtils;
import org.apache.tika.parser.microsoft.fsshttpb.ExGuid;
import org.junit.jupiter.api.Test;

public class TestFssHttpb {

    @Test
    public void testBasic() throws Exception {
        byte[] fileBytes = FileUtils.readFileToByteArray(new File("C:\\lucidworks\\testOneNoteMultiplePages.one"));
        AtomicReference<ExGuid> storageIndexExGuid = new AtomicReference<>();
        List<DataElement> dataElements = DataElementUtils.BuildDataElements(fileBytes, storageIndexExGuid);
        for (DataElement de : dataElements) {
            System.out.println(de);
        }
    }
}
