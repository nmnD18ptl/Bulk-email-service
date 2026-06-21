package com.bulkemail.pro.model.dto;

import java.util.List;

/**
 * Returned by POST /api/contacts/import/preview.
 * Contains the raw column headers and up to 5 sample rows so the frontend
 * can render the column-mapping UI without importing anything yet.
 */
public record ImportPreviewResponse(
        List<String> headers,
        List<List<String>> sampleRows,
        int totalRows
) {}
