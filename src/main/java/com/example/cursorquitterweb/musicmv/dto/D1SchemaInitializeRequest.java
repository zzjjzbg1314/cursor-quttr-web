package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;

/** Explicit confirmation for initializing the dedicated Music MV D1. */
public class D1SchemaInitializeRequest {
    @NotBlank
    private String expectedDatabaseId;

    public String getExpectedDatabaseId() {
        return expectedDatabaseId;
    }

    public void setExpectedDatabaseId(String expectedDatabaseId) {
        this.expectedDatabaseId = expectedDatabaseId;
    }
}
