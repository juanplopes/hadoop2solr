package com.bixolabs.tools;

import java.io.IOException;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.Properties;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;

import cascading.flow.Flow;
import cascading.flow.FlowConnector;
import cascading.flow.FlowProcess;
import cascading.operation.BaseOperation;
import cascading.operation.Function;
import cascading.operation.FunctionCall;
import cascading.operation.filter.Sample;
import cascading.pipe.Each;
import cascading.pipe.GroupBy;
import cascading.pipe.Pipe;
import cascading.scheme.SequenceFile;
import cascading.tap.Hfs;
import cascading.tap.Tap;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;

import com.bixolabs.cascading.HadoopUtils;
import com.bixolabs.cascading.LoggingUtils;
import com.bixolabs.cascading.NullContext;
import com.bixolabs.index.IndexScheme;

public class IndexWorkflow {
    
    // Names of fields in the CrawlDB data we're processing
    public static final String URL_FN = "url";
    public static final String SCORE_FN = "score";
    public static final String DOMAIN_DEPTH_FN = "depth";
    public static final String STATUS_FN = "status";
    public static final String STATUS_TIME_FN = "statustime";
    public static final String STATUS_MSG_FN = "statusmsg";
    
    public static final Fields CRAWLDB_FIELDS = new Fields(URL_FN, SCORE_FN, DOMAIN_DEPTH_FN, STATUS_FN, STATUS_TIME_FN, STATUS_MSG_FN);
    
    // We use the same fieldnames for the Solr index, plus one new one for
    // the domain name that we extract from the URL.
    public static final String DOMAIN_FN = "domain";

    public static final Fields SOLR_FIELDS = new Fields(URL_FN, DOMAIN_FN, STATUS_FN, STATUS_TIME_FN, STATUS_MSG_FN);

    // Lucene settings (stored vs. unstored) for each of the SOLR_FIELDS
    // If it's stored, we can retrieve its original value.
    private static final Store[] STORE_SETTINGS = {
        Store.YES,                          // "url"
        Store.YES,                          // "domain"
        Store.YES,                          // "status"
        Store.YES,                          // "statustime"
        Store.YES,                          // "statusmsg"
    };

    // Lucene settings (indexed vs. unindexed) for each of the SOLR_FIELDS
    // If it's anything but Index.NO, we can search on it.
    private static final Index[] INDEX_SETTINGS = { 
        Index.NOT_ANALYZED_NO_NORMS,        // "url"
        Index.NOT_ANALYZED_NO_NORMS,        // "domain"
        Index.NOT_ANALYZED_NO_NORMS,        // "status"
        Index.NOT_ANALYZED_NO_NORMS,        // "statustime"
        Index.ANALYZED,                     // "statusmsg"
    };

    private static final int MAX_FIELD_LENGTH = 512;
    
    @SuppressWarnings("serial")
    private static class ExtractDomain extends BaseOperation<NullContext> implements Function<NullContext> {
        
        public ExtractDomain() {
            super(SOLR_FIELDS);
        }

        @Override
        public void operate(FlowProcess flowProcess, FunctionCall<NullContext> functionCall) {
            TupleEntry in = functionCall.getArguments();

            String url = in.getString(URL_FN);
            String status = in.getString(STATUS_FN);
            String statusTime = in.getString(STATUS_TIME_FN);
            String statusMsg = in.getString(STATUS_MSG_FN);
            
            try {
                URL realUrl = new URL(url);
                String domain = realUrl.getHost();
                Tuple out = new Tuple(url, domain, status, statusTime, statusMsg);
                functionCall.getOutputCollector().add(out);
            } catch (Exception e) {
                // Ignore invalid URLs
            }
        }
    }
    
    // =========================================================================================
    
	public static Flow createFlow(IndexOptions options)  throws IOException {
	    JobConf conf = HadoopUtils.getDefaultJobConf();
	    conf.setNumReduceTasks(1);
	    
	    // Set up the input and output paths
        Path inputDirPath = new Path(options.getInputDir());
        FileSystem fs = inputDirPath.getFileSystem(conf);
        if (!fs.exists(inputDirPath)) {
            throw new InvalidParameterException("Input directory doesn't exist: " + inputDirPath);
        }

        Path outputDirPath = new Path(options.getOutputDir());
        FileSystem workingFs = outputDirPath.getFileSystem(conf);
        if (!workingFs.exists(outputDirPath)) {
            if (!workingFs.mkdirs(outputDirPath)) {
                throw new InvalidParameterException("Output directory doesn't exist and couldn't be created: " + outputDirPath);
            }
        }

        // Read in from the sequence file, take a slice of the data, and write it out.
        Tap urlSource = new Hfs(new SequenceFile(CRAWLDB_FIELDS), inputDirPath.toString());
        Pipe urlPipe = new Pipe("crawldb urls");
        urlPipe = new Each(urlPipe, new Sample(options.getPercent() / IndexOptions.PROCESS_ALL_ENTRIES));
        urlPipe = new Each(urlPipe, new ExtractDomain());
        
        // Force a reduce step, so that we can control the number of outputs.
        urlPipe = new GroupBy(urlPipe);
        
        Tap solrIndexSink = new Hfs(new IndexScheme(SOLR_FIELDS,
                                                    STORE_SETTINGS, 
                                                    INDEX_SETTINGS, 
                                                    StandardAnalyzer.class, 
                                                    MAX_FIELD_LENGTH),
                                                    outputDirPath.toString(),
                                                    true);

        Properties props = HadoopUtils.getDefaultProperties(IndexWorkflow.class, false, conf);
        LoggingUtils.setLoggingProperties(props, options.getLogLevel());
        FlowConnector flowConnector = new FlowConnector(props);
        return flowConnector.connect(urlSource, solrIndexSink, urlPipe);
    }
}
