package com.jalalidesk.jclockplus;

final class DimSchedule {
    static final int DEFAULT_START = 22 * 60;
    static final int DEFAULT_END = 7 * 60;

    private DimSchedule() {}

    static boolean contains(int minute, int start, int end) {
        if (start == end) {
            return false;
        }
        if (start < end) {
            return minute >= start && minute < end;
        }
        return minute >= start || minute < end;
    }
}
