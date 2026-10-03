package com.personal.ai.api.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** RFC 5545(iCalendar) 문자열을 만든다. 시간대 정의(VTIMEZONE)를 피하려고 시각은 모두 UTC(Z)로 쓴다. */
final class IcsWriter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_OCTETS = 75;

    private IcsWriter() {}

    /** allDay가 있으면 종일 일정(종료일은 다음 날), 없으면 start~end 시각 일정. */
    record Event(String uid, String summary, String description, LocalDate allDay, Instant start, Instant end) {
        static Event allDay(String uid, String summary, String description, LocalDate date) {
            return new Event(uid, summary, description, date, null, null);
        }

        static Event timed(String uid, String summary, String description, Instant start, Instant end) {
            return new Event(uid, summary, description, null, start, end);
        }
    }

    static String calendar(String name, List<Event> events, Instant stamp) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//personal-ai//KO");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:" + escape(name));
        line(sb, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
        line(sb, "X-PUBLISHED-TTL:PT1H");
        for (Event e : events) {
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:" + e.uid());
            line(sb, "DTSTAMP:" + DATE_TIME.format(stamp));
            if (e.allDay() != null) {
                line(sb, "DTSTART;VALUE=DATE:" + DATE.format(e.allDay()));
                line(sb, "DTEND;VALUE=DATE:" + DATE.format(e.allDay().plusDays(1)));
            } else {
                line(sb, "DTSTART:" + DATE_TIME.format(e.start()));
                line(sb, "DTEND:" + DATE_TIME.format(e.end()));
            }
            line(sb, "SUMMARY:" + escape(e.summary()));
            if (e.description() != null && !e.description().isBlank()) {
                line(sb, "DESCRIPTION:" + escape(e.description()));
            }
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    /** 텍스트 값 이스케이프: 역슬래시, 세미콜론, 쉼표, 줄바꿈(\n). 순서가 중요하다(역슬래시를 먼저). */
    static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** 한 줄은 75옥텟을 넘지 못하므로 넘으면 CRLF + 공백으로 이어 쓴다. UTF-8 문자 중간에서는 자르지 않는다. */
    static void line(StringBuilder out, String line) {
        int octets = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > MAX_OCTETS) {
                out.append("\r\n ");
                octets = 1; // 이어지는 줄은 공백 한 칸으로 시작한다
            }
            out.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        out.append("\r\n");
    }
}
