package org.apache.tika.parser.pdf;

public class OCRPageCounter {

    private int count;

    public void increment() {
        count++;
    }

    public int getCount() {
        return count;
    }
}
