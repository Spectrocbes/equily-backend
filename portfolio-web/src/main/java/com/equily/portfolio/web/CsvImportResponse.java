package com.equily.portfolio.web;

import java.util.List;

public record CsvImportResponse(int imported, int skipped, int errors, List<String> errorDetails) {}
