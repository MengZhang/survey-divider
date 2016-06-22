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
    long start1 = System.currentTimeMillis();
    PartitionBuilder partition = new PartitionBuilder(inputFile);
    // Time check
    long runtime1 = System.currentTimeMillis() - start1;
    long start2 = System.currentTimeMillis();
    // A do nothing loop
    for(byte[] part : partition) {}
    long runtime2 = System.currentTimeMillis() - start2;
    long start3 = System.currentTimeMillis();
    int i=0;
    for(byte[] part : partition) {
      System.out.println("Found partition #" + (++i));
    }
    long runtime3 = System.currentTimeMillis() - start3;
    long start4 = System.currentTimeMillis();
    for(byte[] part : partition) {
      System.out.println(new String(part, "UTF-8"));
    }
    long runtime4 = System.currentTimeMillis() - start4;
    long start5 = System.currentTimeMillis();
    partition.writeFiles();
    long runtime5 = System.currentTimeMillis() - start5;
    long total = System.currentTimeMillis() - start1;
    System.out.println("[RUNTIME] allocation:   " + runtime1 + " ms");
    System.out.println("[RUNTIME] pure loop:    " + runtime2 + " ms");
    System.out.println("[RUNTIME] counter loop: " + runtime3 + " ms");
    System.out.println("[RUNTIME] display loop: " + runtime4 + " ms");
    System.out.println("Results:");
    System.out.println("[RUNTIME] TOTAL: " + total + " ms");
  }
}
