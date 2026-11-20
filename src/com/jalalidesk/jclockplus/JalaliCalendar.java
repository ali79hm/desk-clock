package com.jalalidesk.jclockplus;

import java.util.Locale;

final class JalaliCalendar {
    // Calendar break years from K. M. Borkowski, The Persian Calendar for 3000 Years.
    private static final int[] BREAK_YEARS = {
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    };

    private JalaliCalendar() {}

    static String date(int year, int month, int day) {
        int jalaliYear = year - 621;
        if (jalaliYear <= BREAK_YEARS[0] || jalaliYear >= BREAK_YEARS[BREAK_YEARS.length - 1]) {
            return null;
        }

        int today = julianDay(year, month, day);
        int dayOfYear = today - farvardinFirst(jalaliYear);
        if (dayOfYear < 0) {
            jalaliYear--;
            dayOfYear = today - farvardinFirst(jalaliYear);
        }

        int jalaliMonth;
        int jalaliDay;
        if (dayOfYear < 186) {
            jalaliMonth = dayOfYear / 31 + 1;
            jalaliDay = dayOfYear % 31 + 1;
        } else {
            jalaliMonth = (dayOfYear - 186) / 30 + 7;
            jalaliDay = (dayOfYear - 186) % 30 + 1;
        }
        return String.format(Locale.US, "%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay);
    }

    private static int farvardinFirst(int jalaliYear) {
        int gregorianYear = jalaliYear + 621;
        int leapDays = -14;
        int lastBreak = BREAK_YEARS[0];
        for (int i = 1; i < BREAK_YEARS.length; i++) {
            int span = BREAK_YEARS[i] - lastBreak;
            if (jalaliYear < BREAK_YEARS[i]) {
                int years = jalaliYear - lastBreak;
                leapDays += (years / 33) * 8 + ((years % 33) + 3) / 4;
                if (span % 33 == 4 && span - years == 4) {
                    leapDays++;
                }
                break;
            }
            leapDays += (span / 33) * 8 + (span % 33) / 4;
            lastBreak = BREAK_YEARS[i];
        }
        int gregorianLeaps = gregorianYear / 4 - ((gregorianYear / 100 + 1) * 3) / 4 - 150;
        return julianDay(gregorianYear, 3, 20 + leapDays - gregorianLeaps);
    }

    private static int julianDay(int year, int month, int day) {
        int janFeb = (14 - month) / 12;
        int shiftedYear = year + 4800 - janFeb;
        int shiftedMonth = month + 12 * janFeb - 3;
        return day + (153 * shiftedMonth + 2) / 5 + 365 * shiftedYear + shiftedYear / 4
                - shiftedYear / 100 + shiftedYear / 400 - 32045;
    }
}
