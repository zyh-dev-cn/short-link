package com.zyh.shortlink.util;

public final class Base62 {

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = CHARS.length();

    private Base62() {
    }

    public static String encode(long value) {
        if (value == 0) {
            return String.valueOf(CHARS.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(CHARS.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }
}
