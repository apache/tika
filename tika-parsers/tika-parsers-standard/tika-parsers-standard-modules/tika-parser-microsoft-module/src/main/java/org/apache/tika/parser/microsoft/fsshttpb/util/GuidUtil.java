package org.apache.tika.parser.microsoft.fsshttpb.util;

import java.util.UUID;

public class GuidUtil {
    public static UUID emptyGuid() {
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
