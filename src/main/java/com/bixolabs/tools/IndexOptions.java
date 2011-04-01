package com.bixolabs.tools;

import org.kohsuke.args4j.Option;

import com.bixolabs.cascading.BaseOptions;

public class IndexOptions extends BaseOptions {
    public static final double PROCESS_ALL_ENTRIES = 100.0;

    private String _inputDir;
    private String _outputDir;
    private double _percent = PROCESS_ALL_ENTRIES;

    @Option(name = "-input", usage = "Directory containing crawlDB files to process", required = true)
    public void setInputDir(String inputDir) {
        _inputDir = inputDir;
    }

    public String getInputDir() {
        return _inputDir;
    }
    
    @Option(name = "-output", usage = "Directory for resulting Lucene index files", required = true)
    public void setOutputDir(String outputDir) {
        _outputDir = outputDir;
    }

    public String getOutputDir() {
        return _outputDir;
    }
    
    @Option(name = "-percent", usage = "percentage of entries to process", required = false)
    public void setPercent(double percent) {
        _percent = percent;
    }

    public double getPercent() {
        return _percent;
    }
    
}
