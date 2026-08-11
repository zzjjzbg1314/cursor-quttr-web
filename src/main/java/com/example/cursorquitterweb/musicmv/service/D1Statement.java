package com.example.cursorquitterweb.musicmv.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class D1Statement {
    private final String sql;
    private final List<Object> params;

    public D1Statement(String sql, List<Object> params) {
        this.sql = sql;
        this.params = params == null ? new ArrayList<Object>() : params;
    }

    public static D1Statement of(String sql, Object... params) {
        return new D1Statement(sql, Arrays.asList(params));
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParams() {
        return params;
    }
}
