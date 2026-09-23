package com.example.cursorquitterweb.musicmv.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class D1QueryResult {
    private final List<Map<String, Object>> rows;
    private final Long lastRowId;
    private final double durationMs;
    private final long rowsRead;
    private final long rowsWritten;

    public D1QueryResult(List<Map<String, Object>> rows, Long lastRowId) {
        this(rows, lastRowId, 0, 0, 0);
    }

    public D1QueryResult(List<Map<String, Object>> rows, Long lastRowId, double durationMs, long rowsRead, long rowsWritten) {
        this.durationMs = durationMs;
        this.rowsRead = rowsRead;
        this.rowsWritten = rowsWritten;
        this.rows = rows == null ? new ArrayList<Map<String, Object>>() : rows;
        this.lastRowId = lastRowId;
    }

    public double getDurationMs() { return durationMs; }
    public long getRowsRead() { return rowsRead; }
    public long getRowsWritten() { return rowsWritten; }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public Long getLastRowId() {
        return lastRowId;
    }

    public Map<String, Object> firstRow() {
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }
}
