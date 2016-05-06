package org.agmip.ui.sdui;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.agmip.translators.csv.CSVInput;
import org.agmip.util.MapUtil;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Meng Zhang
 */
public class SDUtil {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SDUtil.class);

    public static HashMap<String, File> scanWthData(File wthData, File workDir) {

        if (wthData.isFile()) {
            String name = wthData.getName().toUpperCase();
            if (name.endsWith(".ZIP")) {
//                return scanWthFolder(unzip(wthData, workDir));
                throw new UnsupportedOperationException("weather ZIP file is not supported yet, please unzip it and try again");
            } else {
                HashMap<String, File> wthFiles = new HashMap();
                scanWthFile(wthData, wthFiles);
                return wthFiles;
            }
        } else {
            return scanWthFolder(wthData);
        }
    }

    private static HashMap<String, File> scanWthFolder(File dir) {

        HashMap<String, File> wthFiles = new HashMap();

        for (File f : dir.listFiles()) {
            scanWthFile(f, wthFiles);
        }

        return wthFiles;
    }

    private static void scanWthFile(File f, HashMap<String, File> wthFiles) {
        String name = f.getName().toUpperCase();
        String wthID = "";
        if (name.endsWith(".WTH")) {
            wthID = name.replaceAll(".WTH", "");

        } else if (name.endsWith(".AGMIP")) {
            wthID = name.replaceAll(".AGMIP", "");
        } else if (name.endsWith(".MET")) {
            wthID = name.replaceAll(".MET", "");
        } else if (name.endsWith(".CSV")) {
            CSVInput translator = new CSVInput();
            try {
                Map data = translator.readFile(f.getPath());
                ArrayList<HashMap> wths = MapUtil.getObjectOr(data, "weathers", new ArrayList());
                wthID = MapUtil.getValueOr(wths.get(0), "wst_id", "") + MapUtil.getValueOr(wths.get(0), "clim_id", "");
            } catch (Exception ex) {
                Logger.getLogger(SDUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            LOG.warn("Detect unsupported extension for weather file : [" + f.getPath() + "]");
            return;
        }
        if (!"".equals(wthID)) {
            if (wthFiles.containsKey(wthID)) {
                LOG.warn("Detect the weather data with repeated WST_ID+CLIMID [" + wthID + "] from [" + f.getName() + "]");
            } else {
                LOG.info("Find [" + wthID + "] weather data");
                wthFiles.put(wthID, f);
            }
        }
    }

    public static HashMap<String, File> scanSoilData(File soilData, File workDir) throws IOException {

        HashMap<String, File> soilFiles = new HashMap();
        File soilWorkDir = mkdir(workDir, "soil_temp", true, true);

        if (soilData.isDirectory()) {
            scanSoilFolder(soilData, soilFiles, soilWorkDir);
        } else {
            scanSoilFile(soilData, soilFiles, soilWorkDir);
        }

        return soilFiles;
    }

    private static void scanSoilFolder(File soilDir, HashMap<String, File> soilFiles, File workDir) throws IOException {
        for (File soilData : soilDir.listFiles()) {

            if (soilData.isDirectory()) {
                File soilWorkDir = mkdir(workDir, soilData.getName(), true, false);
                scanSoilFolder(soilData, soilFiles, soilWorkDir);
            } else {
                scanSoilFile(soilData, soilFiles, workDir);
            }

        }
    }

    private static void scanSoilFile(File soilFile, HashMap<String, File> soilFiles, File workDir) throws IOException {

        String name = soilFile.getName().toUpperCase();
        if (name.endsWith(".SOL")) {
            scanDssatSoilFile(soilFile, soilFiles, workDir);
        } else if (name.endsWith(".CSV")) {
            scanCsvSoilFile(soilFile, soilFiles, workDir);
        } else if (soilFile.getName().toUpperCase().endsWith(".ZIP")) {

            ZipFile zf = new ZipFile(soilFile);
            Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = (ZipEntry) e.nextElement();
                String zname = ze.getName().toUpperCase();
                if (zname.endsWith(".SOL")) {
                    scanDssatSoilFile(zf.getInputStream(ze), soilFiles, workDir);
                } else if (zname.endsWith(".CSV")) {
                    scanCsvSoilFile(zf.getInputStream(ze), soilFiles, workDir);
                }
            }
            zf.close();

        } else {
            LOG.warn("Detect unsupported extension for soil file : [" + soilFile.getPath() + "]");
        }
    }

    private static void scanDssatSoilFile(File soilFile, HashMap<String, File> soilFiles, File workDir) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(soilFile)) {
            scanDssatSoilFile(fis, soilFiles, workDir);
        }
    }

    private static void scanDssatSoilFile(InputStream is, HashMap<String, File> soilFiles, File workDir) throws FileNotFoundException, IOException {

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            ArrayList<String> commenLines = new ArrayList();
            ArrayList<String> siteLines = new ArrayList();
            String soilID = null;
            File divSoilFile = null;
            while ((line = br.readLine()) != null) {
                // Read SOILS Info
                if (line.startsWith("*")) {
                    if (line.startsWith("*SOIL")) {
                        commenLines.add(line);
                    } else {
                        if (divSoilFile != null) {
                            try (BufferedWriter bw = new BufferedWriter(new FileWriter(divSoilFile, true))) {
                                writeLines(bw, commenLines);
                                writeLines(bw, siteLines);
                                soilFiles.put(soilID, divSoilFile);
                                LOG.info("Find [" + soilID + "] soil data");
                            }
                        }
                        soilID = substring(line, 1, 12).trim(); // read one more bit to make sure we won't lose anything of SOIL_ID
                        divSoilFile = mkfile(workDir, soilID + ".sol", true);
                        siteLines.clear();
                        siteLines.add(line);
                    }
                } else {
                    if (soilID == null) {
                        commenLines.add(line);
                    } else {
                        siteLines.add(line);
                    }
                }
            }

            // Write the last file
            if (divSoilFile != null) {
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(divSoilFile, true))) {
                    writeLines(bw, commenLines);
                    writeLines(bw, siteLines);
                }
            }
        }
    }

    private static void scanCsvSoilFile(File soilFile, HashMap<String, File> soilFiles, File workDir) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(soilFile)) {
            scanCsvSoilFile(fis, soilFiles, workDir);
        }
    }

    private static void scanCsvSoilFile(InputStream is, HashMap<String, File> soilFiles, File workDir) throws FileNotFoundException, IOException {

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            HashMap<String, File> idxLookupMap = new HashMap();
            String titleLine = "";
            boolean isLayerData = false;
            int soilIdIdx = 1;
//            ArrayList<String> buffer = new ArrayList();
            String lastIdx = null;
            String soilID;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#,")) {

                    // title line for soil profile data
                    titleLine = line;
                    isLayerData = false;
                    List<String> titles = Arrays.asList(line.toLowerCase().split(","));
                    soilIdIdx = titles.indexOf("soil_id");
                    if (soilIdIdx < 0) {
                        LOG.error("Could not detect soil_id in the title line of Soil CSV file");
                    }
                    lastIdx = null;

                } else if (line.startsWith("%,")) {
                    // title line for soil layer data
                    titleLine = line;
                    isLayerData = true;
                    lastIdx = null;

                } else if (line.startsWith("!") || line.trim().equals("")) {
                    // blank line
                } else {
                    String[] values = line.split(",");
                    String idx = values[0].trim();

                    if (isLayerData) {

                        File divSoilFile = idxLookupMap.get(idx);
                        if (divSoilFile != null) {
                            try (BufferedWriter bw = new BufferedWriter(new FileWriter(divSoilFile, true))) {
                                if (!idx.equals(lastIdx)) {
                                    bw.write(titleLine);
                                    bw.newLine();
                                    lastIdx = idx;
                                }
                                bw.write(line);
                                bw.newLine();
                            }
                        } else {
                            LOG.error("Detect line with uncognized index in the soil layer data in soil CSV file, line = [" + line + "]");
                        }

                    } else {
                        if (soilIdIdx > 0) {
                            soilID = values[soilIdIdx].trim();
                            File divSoilFile = mkfile(workDir, soilID + ".csv", true);
                            try (BufferedWriter bw = new BufferedWriter(new FileWriter(divSoilFile, false))) {
                                bw.write(titleLine);
                                bw.newLine();
                                bw.write(line);
                                bw.newLine();
                                soilFiles.put(soilID, divSoilFile);
                                idxLookupMap.put(idx, divSoilFile);
                                LOG.info("Find [" + soilID + "] soil data");
                            }
                        }
                    }
                }
            }
        }
    }

    public static void divideExpByWth(File expFile, HashMap<String, File> soilFiles, File outDir) throws IOException {
        
        if (expFile.isDirectory()) {

        } else if (expFile.getName().toUpperCase().endsWith(".ZIP")) {

        } else if (expFile.getName().toUpperCase().endsWith(".CSV")) {
            divideExpCsvFileByWth(expFile, soilFiles, outDir);
        }
    }

    public static void divideExpCsvFileByWth(File expFile, HashMap<String, File> soilFiles, File outDir) throws FileNotFoundException, IOException {

        try (BufferedReader br = new BufferedReader(new FileReader(expFile))) {

            HashMap<String, File> idxLookupMap = new HashMap();
            String line;
            String titleLine = "";
            int exnameIdx = -1;
            int wstIdIdx = -1;
//            int climIdIdx = -1;
            int soilIdIdx = -1;
            String lastIdx = null;
            boolean isProfileData = false;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("!") || line.trim().equals("")) {
                } else if (line.startsWith("#")) {

                    titleLine = line;
                    List<String> titles = Arrays.asList(line.toLowerCase().split(","));
                    exnameIdx = titles.indexOf("exname");
                    wstIdIdx = titles.indexOf("wst_id");
//                    climIdIdx = titles.indexOf("clim_id");
                    soilIdIdx = titles.indexOf("soil_id");
                    if (exnameIdx > 0) {
                        isProfileData = true;
                    } else {
                        isProfileData = false;
                    }
                    lastIdx = null;

                } else if (line.startsWith("%")) {

                    titleLine = line;
                    isProfileData = false;
                    lastIdx = null;

                } else {

                    String[] values = line.split(",");
                    String idx = values[0];

                    if (isProfileData) {

                        String exname = values[exnameIdx];
                        String soilId;
                        if (soilIdIdx > 0) {
                            soilId = values[soilIdIdx];
                        } else {
                            soilId = "Unknown";
                            LOG.warn("Detect missing soil_id in experiment [" + exname + "]");
                        }
                        String wstId;
                        if (wstIdIdx > 0) {
                            wstId = values[wstIdIdx];
                        } else {
                            wstId = "Unknown";
                            LOG.warn("Detect missing wst_id in experiment [" + exname + "]");
                        }

                        // Prepare deploy files
                        File deployDir = mkdir(outDir, wstId, true, false);
                        // Deploy experiment file
                        File divExpFile = mkfile(deployDir, exname + ".csv", true);
                        try (BufferedWriter bw = new BufferedWriter(new FileWriter(divExpFile, false))) {
                            bw.write(titleLine);
                            bw.newLine();
                            bw.write(line);
                            bw.newLine();
                            bw.flush();
                            if (!idx.equals("*")) {
                                idxLookupMap.put(idx, divExpFile);
                            }
                            LOG.info("Find [" + exname + "] experiment data");
                        }
                        // Deploy soil file
                        if (soilFiles.containsKey(soilId)) {
                            deployFile(deployDir, soilFiles.get(soilId));
                        }

                    } else {

                        File divExpFile = idxLookupMap.get(idx);
                        if (divExpFile != null) {
                            try (BufferedWriter bw = new BufferedWriter(new FileWriter(divExpFile, true))) {
                                if (!idx.equals(lastIdx)) {
                                    bw.write(titleLine);
                                    bw.newLine();
                                    lastIdx = idx;
                                }
                                bw.write(line);
                                bw.newLine();
                            }
                        } else {
                            LOG.error("Detect line with uncognized index in the experiment event data in survey CSV file, line = [" + line + "]");
                        }

                    }
                }

            }
        }
    }
    
    public static void deployCulFileByWth(File culFile, File workDir) throws IOException {
        
        if (culFile.getName().toLowerCase().endsWith(".zip")) {
            try (ZipFile zf = new ZipFile(culFile)) {
                Enumeration<? extends ZipEntry> e = zf.entries();
//                File culDir = null;
                while (e.hasMoreElements()) {
                    ZipEntry ze = (ZipEntry) e.nextElement();
                    String zname = ze.getName();
                    if (ze.isDirectory()) {
//                        for (File dir : workDir.listFiles()) {
//                            if (dir.isDirectory()) {
//                                culDir = mkdir(dir, zname, true, true);
//                            }
//                        }
                    } else {
                        for (File dir : workDir.listFiles()) {
                            if (dir.isDirectory()) {
                                deployFile(dir, zf.getInputStream(ze), zname);
                            }
                        }
                    }
                }
            }
        } else {
            LOG.warn("Detect unsupported file extension for cultivar package : [" + culFile.getPath() + "]");
        }
        
    }
    
    public static void createBatchFilebyWth(File workDir, File fieldDomeFile, File seasonalDome, File linkage) throws IOException {
        
        File batch = SDUtil.mkfile(workDir, "../runAll.bat");
        
        if (batch.exists()) {
            batch.delete();
        }
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(batch, false))) {
            bw.write("@ECHO OFF\r\n\r\n");
            bw.write(":: Set the path for your quaui jar file here\r\n");
            bw.write("set quadui=D:\\workData\\DSSAT_TOOL\\QuadUI_v1.3.7-beta2\\quadui-1.3.7-beta2.jar\r\n");
            bw.newLine();
            bw.write(":: Set the path for your acmoui jar file here\r\n");
            bw.write("set acmui=D:\\workData\\DSSAT_TOOL\\acmoui-1.2.1-release\\acmoui-1.2.1.jar\r\n");
            bw.newLine();
            bw.write(":: Set the version for your DSSAT model here\r\n");
            bw.write("set dssat_version=45\r\n");
            bw.write(":: Set the path for your DSSAT model here\r\n");
            bw.write("set dssat_path=C:\\dssat45\r\n");
            bw.newLine();
            bw.write(":: Set the path for your APSIM model here\r\n");
            bw.write("set apsim_path=C:\\Program Files (x86)\\Apsim75-r3008\r\n");
            bw.write(":: Set the crop for your APSIM model here\r\n");
            bw.write(":: Only required when using system default cultivar\r\n");
            bw.write("set apsim_crop=unknown\r\n");
            bw.write("set apsim_crop_path=unknown\r\n");
            bw.newLine();
            bw.write(":: Set DOME file path here\r\n");
            bw.write("set overlay=" + fieldDomeFile.getPath() + "\r\n");
            bw.write("set strategy=" + seasonalDome.getPath() + "\r\n");
            bw.write("set linkage=" + linkage.getPath() + "\r\n");
            bw.newLine();
            bw.write(":: remove old log files generated by QuadUI and ACMOUI\r\n");
            bw.write("del *.log\r\n");
            bw.newLine();
            bw.flush();
            
            for (File dir : workDir.listFiles()) {
                if (dir.isDirectory()) {
                    
                    LOG.info("Create auto-run batch for weahter data " + dir.getName());
                    File surveyZip = mkfile(dir, "survey_" + dir.getName() + ".zip");
                    //zip the folder and remove inside files and create survey.zip
                    zipAll(dir, surveyZip, true);
                    // add batch commands in the bat file
                    bw.write(":: " + dir.getName() + "\r\n");
                    bw.write("set survey=" + surveyZip.getPath() + "\r\n");
                    bw.write("set output=" + mkdir(dir, "output", true, true).getPath() + "\r\n");
                    bw.write("java -jar %quadui% -cli -clean -s -ADJ %survey% %linkage% %overlay% %strategy% %output%\r\n");
                    bw.write("cd " + mkdir(dir, "output" + File.separator + "DSSAT", false, false) + "\r\n");
                    bw.write("%dssat_path%\\dscsm0%dssat_version% b dssbatch.v%dssat_version%\r\n");
                    bw.write("cd " + mkdir(dir, "output" + File.separator + "APSIM", false, false) + "\r\n");
                    bw.write("echo run APSIM\r\n");
                    bw.write("if not %apsim_crop%==unknow (\r\n");
                    bw.write("  if not exist %apsim_crop%.xml (\r\n");
                    bw.write("    copy  %apsim_crop_path% %apsim_crop%.xml\r\n");
                    bw.write("  )\r\n");
                    bw.write(")\r\n");
                    bw.write("\"%apsim_path%\\Model\\Apsim\" AgMip.apsim\r\n");
                    bw.write("cd " + workDir.getParent() + "\r\n");
                    bw.write("java -jar %acmui% -cli -dssat \"" + mkdir(dir, "output" + File.separator + "DSSAT", false, false) + "\"\r\n");
                    bw.write("java -jar %acmui% -cli -apsim \"" + mkdir(dir, "output" + File.separator + "APSIM", false, false) + "\"\r\n");
                    bw.newLine();
                }
            }
            bw.write("All jobs are completed!");
            bw.write("pause\r\n");
            bw.write("exit");
        }
    }

    public static File mkdir(File base, String folder, boolean ifMkdir, boolean ifClean) {

        if (folder == null || folder.equals("") || base == null) {
            return base;
        }

        if (base.isFile()) {
            base = base.getParentFile();
        }
        File newDir = new File(base.getPath() + File.separator + folder);

        if (ifClean && newDir.exists()) {
            cleanFolder(newDir);
        }

        if (ifMkdir && !newDir.exists()) {
            newDir.mkdirs();
        }

        return newDir;
    }

    public static File mkfile(File base, String fileName) {
        return mkfile(base, fileName, true);
    }

    public static File mkfile(File base, String fileName, boolean ifMkdir) {

        if (fileName == null || fileName.equals("") || base == null) {
            return base;
        }

        if (base.isFile()) {
            base = base.getParentFile();
        }
        
        File ret = new File(base.getPath() + File.separator + fileName);

        if (ifMkdir && !ret.getParentFile().exists()) {
            ret.getParentFile().mkdirs();
        }

        return ret;
    }
    
    public static void cleanFolder(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                cleanFolder(f);
            } else {
                f.delete();
            }
        }
        dir.delete();
    }

    public static void deployFile(File depDir, File f) throws FileNotFoundException, IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            deployFile(depDir, fis, f.getName());
        }
    }

    public static void deployFile(File depDir, FileInputStream fis, String fileName) throws IOException {

        File outFile = SDUtil.mkfile(depDir, fileName);
        if (outFile.exists()) {
            return;
        }
        FileOutputStream fos = new FileOutputStream(outFile);
        try (FileChannel out = fos.getChannel(); FileChannel in = fis.getChannel()) {
            in.transferTo(0, in.size(), out);
        }
    }

    public static void deployFile(File depDir, InputStream is, String fileName) throws IOException {
        File outFile = SDUtil.mkfile(depDir, fileName);
        if (outFile.exists()) {
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[4096];
            int i;
            while ((i = is.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        }
    }

    public static String substring(String base, int start, int end) {
        if (end < start) {
            return null;
        }
        if (base.length() < end) {
            return base.substring(start);
        } else {
            return base.substring(start, end);
        }
    }

    public static void writeLines(BufferedWriter bw, ArrayList<String> lines) throws IOException {
        for (String line : lines) {
            bw.write(line);
            bw.newLine();
        }
        bw.flush();
    }

    public static File unzip(File zip, File workDir) {
        File dir = new File("");
        // TODO General unzip utility
        return dir;
    }

    public static String getWstID(String wthID) {
        int length = wthID.length();
        if (length <= 3) {
            return wthID;
        } else {
            return wthID.substring(0, 4);
        }
    }
    
    public static void zipAll(File dir, File outputFile, boolean ifRemove) throws FileNotFoundException, IOException {
        
        if (outputFile.exists()) {
            outputFile.delete();
        }
        
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outputFile))) {
            
            String zipName = outputFile.getName();
            
            for (File file : dir.listFiles()) {
                
                if (file.isDirectory() && !file.getName().equals("output")) {
                    zipAll(file, file.getName(), out, ifRemove);
                } else if (file.isFile() && !file.getName().equals(zipName)) {
                    zipOne(file, null, out, ifRemove);
                }
            }
        }
    }
    
    private static void zipOne(File file, String baseDir, ZipOutputStream out, boolean ifRemove) throws IOException {
        ZipEntry entry;
        byte[] data = new byte[1024];
        if (baseDir != null && !baseDir.trim().equals("")) {
            entry = new ZipEntry(baseDir + "/" + file.getName());
        } else {
            entry = new ZipEntry(file.getName());
        }
        out.putNextEntry(entry);

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {

            int count;
            while ((count = bis.read(data)) != -1) {
                out.write(data, 0, count);
            }
        }
        
        if (ifRemove) {
            file.delete();
        }
    }
    
    private static void zipAll(File dir, String baseDir, ZipOutputStream out, boolean ifRemove) throws IOException {
        
        ZipEntry entry;
        if (baseDir != null) {
            entry = new ZipEntry(baseDir + "/" + dir.getName() + "/");
        } else {
            entry = new ZipEntry(dir.getName() + "/");
        }
        out.putNextEntry(entry);
        
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                zipAll(f, baseDir, out, ifRemove);
            } else if (f.isFile()) {
                zipOne(f, baseDir, out, ifRemove);
            }
        }
        if (ifRemove) {
            dir.delete();
        }
    }
}
