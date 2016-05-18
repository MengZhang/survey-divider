package org.agmip.tools.surveydivider;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Meng Zhang
 */
public class Main {
    
    
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Main.class);
    private static File surveyFile;
    private static File linkageFile;
    private static File wthData;
    private static File culData;
    private static File soilData;
    private static File overlyDomeFile;
    private static File strategyDomeFile;
//    private static File outputDir = new File("output");
    
    private static File workDir;
    private static HashMap<String, File> wthFiles;
    private static HashMap<String, File> soilFiles;
    
    public static void main(String[] args) throws Exception {
        
        surveyFile = new File(args[0]);
        soilData = new File(args[1]);
        wthData = new File(args[2]);
        culData = new File(args[3]);
        linkageFile = new File(args[4]);
        overlyDomeFile = new File(args[5]);
        strategyDomeFile = new File(args[6]);
        workDir = new File(args[7]);
        scanData();
        divideByWth();
    }
    
    public static void scanData() throws IOException {
        wthFiles = SDUtil.scanWthData(wthData, workDir);
        soilFiles = SDUtil.scanSoilData(soilData, workDir);
        
    }
    
    private static void divideByWth() throws IOException {
        
        File divDir = SDUtil.mkdir(workDir, "divided", true, true);
        
        // Deploy weather files
        for (String wthID : wthFiles.keySet()) {
            String wstID = SDUtil.getWstID(wthID);
            File wthDir = SDUtil.mkdir(divDir, wstID, true, false);
            SDUtil.deployFile(wthDir, wthFiles.get(wthID));
        }
        
        // SCAN exp file, devide it and deploy the small pieces of exp file
        // deploy the soil file based on the soil_id list
        SDUtil.divideExpByWth(surveyFile, soilFiles, divDir);
        SDUtil.deployCulFileByWth(culData, divDir);
        
        // make input package for QuadUI
        SDUtil.createBatchFilebyWth(divDir, overlyDomeFile, strategyDomeFile, linkageFile);
    }
}
