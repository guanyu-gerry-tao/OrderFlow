package com.orderflow.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Writes local benchmark evidence in machine-readable and reviewer-readable forms.
 */
class BenchmarkReportWriter {

    private final ObjectMapper objectMapper;

    /**
     * Creates the report writer.
     *
     * @param objectMapper JSON mapper
     */
    BenchmarkReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes a benchmark report as JSON plus a Markdown summary.
     *
     * @param outputDirectory report output directory
     * @param fileStem file name without extension
     * @param mode benchmark mode
     * @param title Markdown title
     * @param report benchmark report payload
     * @return generated report file paths
     * @throws IOException when report files cannot be written
     */
    BenchmarkReportFiles writeReport(
            Path outputDirectory,
            String fileStem,
            String mode,
            String title,
            ObjectNode report
    ) throws IOException {
        Files.createDirectories(outputDirectory);
        report.put("generatedAt", Instant.now().toString());

        Path jsonPath = outputDirectory.resolve(fileStem + ".json");
        Path markdownPath = outputDirectory.resolve(fileStem + ".md");

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), report);
        Files.writeString(markdownPath, buildMarkdown(title, mode, report));

        return new BenchmarkReportFiles(jsonPath, markdownPath);
    }

    private String buildMarkdown(String title, String mode, ObjectNode report) throws IOException {
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);

        return "# " + title + "\n\n"
                + "Mode: `" + mode + "`\n\n"
                + "This report was generated from the current local codebase. "
                + "It is synthetic benchmark evidence, not production traffic.\n\n"
                + "```json\n"
                + prettyJson
                + "\n```\n";
    }
}
