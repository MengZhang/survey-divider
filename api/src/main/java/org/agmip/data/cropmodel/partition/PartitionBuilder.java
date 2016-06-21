package org.agmip.data.cropmodel.partition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartitionBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionBuilder.class);
  private static final JsonFactory JSON = new JsonFactory();
  private byte[] json;

  private enum ACESegmentType {
    EXPERIMENT,
    SOIL,
    WEATHER,
    UNSET
  }

  public PartitionBuilder(Path jsonFile) throws IOException {
    this.json = Files.readAllBytes(jsonFile);
  }

  public int[] analyze() throws IOException {
    int[] analysis = {0,0,0};
    try(JsonParser parser = JSON.createParser(this.json)) {
      JsonToken t;
      String currentSection = "";
      int currentDepth = 0;
      while ((t = parser.nextToken()) != null) {
        switch (t) {
        case START_ARRAY:
          currentDepth++;
          break;
        case START_OBJECT:
          currentDepth++;
          if (currentDepth == 3) {
            switch (currentSection.toLowerCase()) {
            case "experiments":
              analysis[0]++;
              break;
            case "soils":
              analysis[1]++;
              break;
            case "weathers":
              analysis[2]++;
              break;
            }
          }
          break;
        case END_ARRAY:
          currentDepth--;
          break;
        case END_OBJECT:
          currentDepth--;
          break;
        case FIELD_NAME:
          if (currentDepth == 1) {
            currentSection = parser.getCurrentName();
          }
          break;
        default:
          break;
        }
      }
    }
    return analysis;
  }

  public int[] markers(String section, int positions) throws IOException {
    int[] markers = new int[positions];
    try(JsonParser parser = JSON.createParser(this.json)) {
      JsonToken t;
      String currentSection = "";
      int currentDepth = 0;
      int cursor = 0;
      int marker = 0;
      while ((t = parser.nextToken()) != null) {
        cursor++;
        switch (t) {
        case START_ARRAY:
          currentDepth++;
          break;
        case START_OBJECT:
          currentDepth++;
          if (currentDepth == 3) {
            if (currentSection.equals(section)) {
              markers[marker] = cursor-1;
              marker++;
            }
          }
          break;
        case END_ARRAY:
          currentDepth--;
          break;
        case END_OBJECT:
          currentDepth--;
          break;
        case FIELD_NAME:
          if (currentDepth == 1) {
            currentSection = parser.getCurrentName();
          }
          break;
        default:
          break;
        }
      }
    }
    return markers;
  }

  private static void skip(JsonParser p, int num) throws IOException {
    for(int i=0; i != num; i++) {
      p.nextToken();
    }
  }
  
  public String[] extractIds(String var, int markers[]) throws IOException {
    String[] id = new String[markers.length];
    for (int i=0; i < markers.length; i++) {
      int marker = markers[i];
      try(JsonParser parser = JSON.createParser(this.json)) {
        JsonToken t;
        skip(parser, marker);
        //String currentSection = "";
        int currentDepth = 0;
        //int cursor = 0;
        //int marker = 0;
        boolean found = false;
        while (((t = parser.nextToken()) != null) && ! found) {
          switch (t) {
          case START_ARRAY:
          case START_OBJECT:
            currentDepth++;
            break;
          case END_ARRAY:
          case END_OBJECT:
            currentDepth--;
            break;
          case FIELD_NAME:
            if (currentDepth == 1) {
              if (parser.getCurrentName().equals(var)) {
                parser.nextToken();
                id[i] = parser.getText();
                found = true;
              }
            }
            break;
          }
        }
      }
    }
    return id;
  }

  public int[][] split(int[] exMarkers, int[] sMarkers, int[] wMarkers,
                       String[] sids, String[] wids) throws IOException {
    int[][] explode = new int[exMarkers.length][3];
    try(JsonParser parser = JSON.createParser(this.json)) {
      JsonToken t;
      String currentSection = "";
      int exMarker = 0;
      int currentDepth = 0;
      boolean foundSid= false;
      boolean foundWid = false;
      int sidMarker = -1;
      int widMarker = -1;
      while((t = parser.nextToken()) != null) {
        switch (t) {
        case START_ARRAY:
          currentDepth++;
          break;
        case END_ARRAY:
          currentDepth--;
          break;
        case START_OBJECT:
          currentDepth++;
          break;
        case END_OBJECT:
          if (currentSection.equals("experiments") && currentDepth == 3) {
            explode[exMarker][0] = exMarkers[exMarker];
            explode[exMarker][1] = sidMarker;
            explode[exMarker][2] = widMarker;
            sidMarker = -1;
            widMarker = -1;
            exMarker++;
            foundWid = false;
            foundSid = false;
          }
          currentDepth--;
          break;
        case FIELD_NAME:
          if (currentDepth == 1) {
            currentSection = parser.getCurrentName();
          }
          if (currentSection.equals("experiments") && currentDepth == 3 && ( ! foundSid || ! foundWid )) {
            if (!foundSid && parser.getCurrentName().equals("sid")) {
              sidMarker = getMarkerMap(parser, sids, sMarkers);
              foundSid = true;
            }
            if (!foundWid && parser.getCurrentName().equals("wid")) {
              widMarker = getMarkerMap(parser, wids, wMarkers);
              foundWid = true;
            }
          }
          break;
        }
      }
    }
    return explode;
  }

  private static int getMarkerMap(JsonParser p, String[] ids, int[] markers) throws IOException {
    p.nextToken();
    String id = p.getText();
    for(int i=0; i < ids.length; i++) {
      if (ids[i].equals(id)) {
        return markers[i];
      }
    }
    return -1;
  }

  public void writeFiles(Path directory, int[][] markers) throws IOException {
    System.out.println("[DEBUG] Output directory: " + directory.toString());
    for(int i=0; i < markers.length; i++) {
      Path outFile = Files.createTempFile(directory, "part", ".json");
      try(JsonGenerator gen = JSON.createGenerator(Files.newOutputStream(outFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
        gen.writeStartObject();
        gen.writeArrayFieldStart("experiments");
        try(JsonParser parser = JSON.createParser(this.json)) {
          skip(parser, markers[i][0]+1);
          gen.copyCurrentStructure(parser);
        }
        gen.writeEndArray();
        gen.writeArrayFieldStart("soils");
        try(JsonParser parser = JSON.createParser(this.json)) {
          skip(parser, markers[i][1]+1);
          gen.copyCurrentStructure(parser);
        }
        gen.writeEndArray();
        gen.writeArrayFieldStart("weathers");
        try(JsonParser parser = JSON.createParser(this.json)) {
          skip(parser, markers[i][2]+1);
          gen.copyCurrentStructure(parser);
        }
        gen.writeEndArray();
        gen.writeEndObject();
        gen.flush();
      }
    }
  }

  public void writeFiles(int[][] markers) throws IOException {
    Path dir = Files.createTempDirectory("ace");
    writeFiles(dir, markers);
  }
}
