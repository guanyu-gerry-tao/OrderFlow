package com.orderflow.benchmark;

import java.nio.file.Path;

/**
 * Holds the JSON and Markdown report paths created by a benchmark run.
 *
 * @param jsonPath JSON report path
 * @param markdownPath Markdown summary path
 */
record BenchmarkReportFiles(Path jsonPath, Path markdownPath) {
}
