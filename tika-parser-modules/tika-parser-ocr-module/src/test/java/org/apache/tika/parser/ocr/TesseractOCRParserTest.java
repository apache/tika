package org.apache.tika.parser.ocr;

import org.apache.tika.parser.external.ExternalParser;

public class TesseractOCRParserTest {

    public static boolean canRun() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        TesseractOCRParserTest tesseractOCRTest = new TesseractOCRParserTest();
        return tesseractOCRTest.canRun(config);
    }

    private boolean canRun(TesseractOCRConfig config) {
        String[] checkCmd = {config.getTesseractPath() + TesseractOCRParser.getTesseractProg()};
        // If Tesseract is not on the path, do not run the test.
        return ExternalParser.check(checkCmd);
    }
}
