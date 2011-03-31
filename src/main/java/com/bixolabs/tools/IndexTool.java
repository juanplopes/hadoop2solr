/**
 * Copyright (c) 2010 TransPac Software, Inc.
 * All rights reserved.
 *
 */
package com.bixolabs.tools;

import org.kohsuke.args4j.CmdLineParser;

import cascading.flow.Flow;
import cascading.flow.PlannerException;

import com.bixolabs.cascading.BaseTool;

public class IndexTool extends BaseTool {
	
    public static void main(String[] args) {
        IndexOptions options = new IndexOptions();
        CmdLineParser parser = parse(args, options);
        
        try {
            Flow flow = IndexWorkflow.createFlow(options);
            if (options.getDOTFile() != null) {
                flow.writeDOT(getDotFileName(options, "index"));
                flow.writeStepsDOT(getStepDotFileName(options, "index"));
            }
            
            flow.complete();
        } catch (PlannerException e) {
            e.writeDOT("build/failed-flow.dot");
            System.err.println("PlannerException: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(-1);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid argument: " + e);
            printUsageAndExit(parser);
        } catch (Throwable t) {
            System.err.println("Exception running tool: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }

    }

}
