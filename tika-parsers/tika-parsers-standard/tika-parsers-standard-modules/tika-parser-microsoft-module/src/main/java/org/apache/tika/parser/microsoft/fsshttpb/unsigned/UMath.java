package org.apache.tika.parser.microsoft.fsshttpb.unsigned;

public final class UMath {

    private UMath() {
    }

    /**
     * Returns the greater of two {@code UByte} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static UByte max(UByte a, UByte b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code UInteger} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static UInteger max(UInteger a, UInteger b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code ULong} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static ULong max(ULong a, ULong b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code UShort} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static UShort max(UShort a, UShort b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code UByte} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static UByte min(UByte a, UByte b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code UInteger} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static UInteger min(UInteger a, UInteger b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code ULong} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static ULong min(ULong a, ULong b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code UShort} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static UShort min(UShort a, UShort b) {
        return a.compareTo(b) < 0 ? a : b;
    }

}
