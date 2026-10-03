package com.personal.ai.api.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsWriterTest {

    private static final Instant STAMP = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void escapesBackslashSemicolonCommaAndNewlines() {
        assertEquals("a\\\\b\\;c\\,d\\ne\\nf", IcsWriter.escape("a\\b;c,d\ne\r\nf"));
    }

    @Test
    void writesTimedAndAllDayEventsWithCrlfAndExclusiveEndDate() {
        String ics = IcsWriter.calendar("Personal AI", List.of(
                IcsWriter.Event.timed("step-1@personal-ai", "학습", "목표", Instant.parse("2026-10-03T12:00:00Z"), Instant.parse("2026-10-03T13:30:00Z")),
                IcsWriter.Event.allDay("deadline-1@personal-ai", "마감", null, LocalDate.parse("2026-12-31"))), STAMP);

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        assertTrue(ics.contains("DTSTART:20261003T120000Z\r\nDTEND:20261003T133000Z\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261231\r\nDTEND;VALUE=DATE:20270101\r\n")); // 종료일은 다음 날(배타적)
        assertTrue(ics.contains("DTSTAMP:20261003T000000Z") || ics.contains("DTSTAMP:20261003"));
        assertFalse(ics.contains("DESCRIPTION:\r\n")); // 비어 있는 설명은 쓰지 않는다
        assertFalse(ics.replace("\r\n", "").contains("\n"));
    }

    @Test
    void foldsLongLinesAt75OctetsWithoutSplittingMultibyteCharacters() {
        StringBuilder out = new StringBuilder();
        IcsWriter.line(out, "SUMMARY:" + "가".repeat(60)); // 한글은 글자당 3옥텟

        String[] lines = out.toString().split("\r\n");
        assertTrue(lines.length > 1);
        for (String l : lines) {
            assertTrue(l.getBytes(StandardCharsets.UTF_8).length <= 75, l);
        }
        // 이어 붙이면(접힌 줄의 앞 공백 제거) 원문이 그대로 복원된다
        String unfolded = out.toString().replace("\r\n ", "").replace("\r\n", "");
        assertEquals("SUMMARY:" + "가".repeat(60), unfolded);
        assertTrue(lines[1].startsWith(" "));
    }
}
