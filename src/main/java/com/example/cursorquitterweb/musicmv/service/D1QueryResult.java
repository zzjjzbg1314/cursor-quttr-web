package com.example.cursorquitterweb.musicmv.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class D1QueryResult {
    private final List<Map<String, Object>> rows;
    private final Long lastRowId;

    public D1QueryResult(List<Map<String, Object>> rows, Long lastRowId) {
        this.rows = rows == null ? new ArrayList<Map<String, Object>>() : rows;
        this.lastRowId = lastRowId;
    }

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
