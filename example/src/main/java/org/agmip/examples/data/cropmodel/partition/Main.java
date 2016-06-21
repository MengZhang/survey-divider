package org.agmip.examples.data.cropmodel.partition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.agmip.data.cropmodel.partition.PartitionBuilder;

public class Main {
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Invalid number of arguments.");
      System.exit(-1);
    }
    Path inputFile = Paths.get(args[0]);
    if (! Files.exists(inputFile)) {
      System.err.println("File does not exist: " + args[0]);
      System.exit(-1);
    }
    PartitionBuilder partition = new PartitionBuilder(inputFile);
    // Time check
    long start1 = System.currentTimeMillis();
    int[] analysis = partition.analyze();
    long runtime1 = System.currentTimeMillis() - start1;
    long start2 = System.currentTimeMillis();
    int[] experimentMarkers = partition.markers("experiments", analysis[0]);
    int[] soilMarkers = partition.markers("soils", analysis[1]);
    int[] weatherMarkers = partition.markers("weathers", analysis[1]);
    long runtime2 = System.currentTimeMillis() - start2;
    long start3 = System.currentTimeMillis();
    String[] sids = partition.extractIds("sid", soilMarkers);
    String[] wids = partition.extractIds("wid", weatherMarkers);
    long runtime3 = System.currentTimeMillis() - start3;
    long start4 = System.currentTimeMillis();
    int[][] exploded = partition.split(experimentMarkers, soilMarkers, weatherMarkers, sids, wids);
    long runtime4 = System.currentTimeMillis() - start4;
    long start5 = System.currentTimeMillis();
    partition.writeFiles(exploded);
    long runtime5 = System.currentTimeMillis() - start5;
    long total = System.currentTimeMillis() - start1;
    System.out.println("[RUNTIME] analyze: " + runtime1 + " ms");
    System.out.println("Results:");
    System.out.println("Experiments: " + analysis[0]);
    System.out.println("Soils:       " + analysis[1]);
    System.out.println("Weathers:    " + analysis[2]);
    System.out.println("[RUNTIME] breakout: " + runtime2 + " ms");
    System.out.println("[RUNTIME] extraction: " + runtime3 + " ms");
    System.out.println("[RUNTIME] explode: " + runtime4 + " ms");
    System.out.println("[RUNTIME] TOTAL: " + total + " ms");
  }
}
