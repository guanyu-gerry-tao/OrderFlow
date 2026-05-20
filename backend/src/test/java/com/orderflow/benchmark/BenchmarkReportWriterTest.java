package com.orderflow.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkReportWriterTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void writesJsonAndMarkdownBenchmarkReports() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode report = objectMapper.createObjectNode();
        report.put("suite", "sample-suite");
        report.put("mode", "improved");

        BenchmarkReportWriter writer = new BenchmarkReportWriter(objectMapper);

        BenchmarkReportFiles files = writer.writeReport(
                temporaryDirectory,
                "sample-suite",
                "improved",
                "Sample Suite",
                report
        );

        assertThat(Files.readString(files.jsonPath())).contains("\"suite\" : \"sample-suite\"");
        assertThat(Files.readString(files.markdownPath())).contains("# Sample Suite");
        assertThat(Files.readString(files.markdownPath())).contains("Mode: `improved`");
    }
}
