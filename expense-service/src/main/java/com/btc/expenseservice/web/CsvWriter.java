package com.btc.expenseservice.web;

import java.util.List;
import java.util.stream.Collectors;

/** RFC 4180 CSV with spreadsheet-formula neutralisation (values starting with = + - @ are prefixed with '). */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static String row(List<?> values) {
        return values.stream().map(CsvWriter::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r") || text.startsWith("'")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
