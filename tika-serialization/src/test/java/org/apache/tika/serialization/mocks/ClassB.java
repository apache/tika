package org.apache.tika.serialization.mocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ClassB extends ClassA {
    private String s = "hello world";
    private Map<String, String> counts = new HashMap<>();
    private Integer[] ints = new Integer[]{1,2,3,4};
    private List<Float> floats = new ArrayList<>();

    public ClassB() {
        floats.add(2.3f);
        floats.add(3.4f);
        counts.put("k1", "v1");
        counts.put("k2", "v2");
    }

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    public Map<String, String> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, String> counts) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ClassB classB = (ClassB) o;
        return Objects.equals(s, classB.s) && Objects.equals(counts, classB.counts) && Arrays.equals(ints, classB.ints) && Objects.equals(floats, classB.floats);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(s);
        result = 31 * result + Objects.hashCode(counts);
        result = 31 * result + Arrays.hashCode(ints);
        result = 31 * result + Objects.hashCode(floats);
        return result;
    }
}
