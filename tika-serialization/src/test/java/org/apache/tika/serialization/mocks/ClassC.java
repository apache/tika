package org.apache.tika.serialization.mocks;

import java.util.Objects;

public class ClassC {

    ClassB classB = new ClassB();

    public ClassB getClassB() {
        return classB;
    }

    public void setClassB(ClassB classB) {
        this.classB = classB;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClassC classC = (ClassC) o;
        return Objects.equals(classB, classC.classB);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(classB);
    }
}
