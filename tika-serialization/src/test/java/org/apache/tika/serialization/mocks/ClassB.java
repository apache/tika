package org.apache.tika.serialization.mocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassB extends ClassA {
    private String s = "hello world";
    private Map<String, Long> counts = new HashMap<>();
    private Integer[] ints = new Integer[]{1,2,3,4};
    private List<Float> floats = new ArrayList<>();

    public ClassB() {
        floats.add(2.3f);
        floats.add(3.4f);
        counts.put("k1", 1l);
        counts.put("k2", 2l);
    }

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    public Map<String, Long> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Long> counts) {
        this.counts = counts;
    }

    public Integer[] getInts() {
        return ints;
    }

    public void setInts(Integer[] ints) {
        this.ints = ints;
    }

    public List<Float> getFloats() {
        return floats;
    }

    public void setFloats(List<Float> floats) {
        this.floats = floats;
    }
}
