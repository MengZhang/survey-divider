package org.agmip.tools.surveydivider;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.agmip.ace.AceDataset;
import org.agmip.ace.AceWeather;
import org.agmip.ace.io.AceGenerator;
import org.agmip.ace.io.AceParser;
import org.agmip.cropmodel.data.partition.PartitionBuilder;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Meng Zhang
 */
public class Main {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        File acebFile = new File(args[0]);
        String workingPath;
        if (args.length < 1) {
            workingPath = ".";
        } else{
            workingPath = args[1];
        }

        AceDataset dataset = AceParser.parseACEB(acebFile);
        PartitionBuilder p = new PartitionBuilder(dataset);//.byWeatherStation();
        p.bySize(8);
        p.byWeatherStation();
        System.out.println("Final partition count: " + p.size());
        for(Map.Entry<String, AceDataset> entry : p.get().entrySet()) {
            Path outputDir = Paths.get(workingPath + File.separator + entry.getKey());
            Files.createDirectories(outputDir);
            AceGenerator.generateACEB(outputDir.resolve("SurveyData.aceb").toFile(), entry.getValue());
        }
    }
    /*
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
    */
}

