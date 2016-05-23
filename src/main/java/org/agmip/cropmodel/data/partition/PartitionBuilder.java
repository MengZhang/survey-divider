package org.agmip.cropmodel.data.partition;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.agmip.ace.AceDataset;
import org.agmip.ace.AceExperiment;
import org.agmip.ace.AceWeather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartitionBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionBuilder.class);
  private final Map<String, AceDataset> partitions = new LinkedHashMap<>();

  public PartitionBuilder(AceDataset dataset) throws Exception {
    dataset.linkDataset();
    partitions.put("", dataset);
  }

  public PartitionBuilder byWeatherStation() {
    try {
      if (this.partitions.size() == 1) {
        AceDataset partition = this.partitions.get("");
        if (partition.getWeathers().size() == 1) {
          return this;
        }
      }
      Map<String, AceDataset> newPartitions = new LinkedHashMap<>();
      Map<String, String> stationMap = new LinkedHashMap<>();
      for(Map.Entry<String,AceDataset> currentPartition : partitions.entrySet()) {
        String prefix = currentPartition.getKey();
        currentPartition.getValue().linkDataset();
        for (AceWeather station : currentPartition.getValue().getWeathers()) {
          AceDataset partition = new AceDataset();
          String wstId = station.getValueOr("wst_id", "UNKNOWN");
          String key = prefix + ((! prefix.equals("")) ? File.separator : "") + wstId;
          stationMap.put(station.getId(), key);
          partition.addWeather(station.rebuildComponent());
          newPartitions.put(key, partition);
        }

        for (AceExperiment ex : currentPartition.getValue().getExperiments()) {
          if (stationMap.containsKey(ex.getWeather().getId())) {
            AceDataset partition = newPartitions.get(stationMap.get(ex.getWeather().getId()));
            partition.addExperiment(ex.rebuildComponent());
            partition.addSoil(ex.getSoil().rebuildComponent());
          }
        }

        for (AceDataset partition : newPartitions.values()) {
          partition.linkDataset();
        }
      }
      this.partitions.clear();
      this.partitions.putAll(newPartitions);
    }
    catch (IOException ex) {
      LOG.error( ex.getMessage() );
    }
    return this;
  }

  public Map<String, AceDataset> get() {
    return this.partitions;
  }

  public int size() {
    return this.partitions.size();
  }

  public PartitionBuilder bySize(int size) {
    try {
      if (this.partitions.size() == 1) {
        AceDataset partition = this.partitions.get("");
        if (partition.getExperiments().size() < size) {
          return this;
        }
      }
      Map<String, AceDataset> newPartitions = new LinkedHashMap<>();
      for (Map.Entry<String, AceDataset> currentPartition : this.partitions.entrySet()) {
        List<AceExperiment> experiments = currentPartition.getValue().getExperiments();
        int exSize = experiments.size();
        String currentKey = currentPartition.getKey();
        if (experiments.size() < size) {
          newPartitions.put(currentKey, currentPartition.getValue());
          continue;
        }
        int partitionCount = 1;
        int counter = 0;
        String key = bySizeKey(currentKey, partitionCount, size, exSize);
        AceDataset firstPartition = new AceDataset();
        newPartitions.put(key, firstPartition);
        for (AceExperiment ex : experiments) {
          AceDataset partition = newPartitions.get(key);
          partition.addExperiment(ex.rebuildComponent());
          partition.addSoil(ex.getSoil().rebuildComponent());
          partition.addWeather(ex.getWeather().rebuildComponent());
          counter++;
          if (counter == size) {
            partition.linkDataset();
            counter = 0;
            partitionCount++;
            key = bySizeKey(currentKey, partitionCount, size, exSize);
            AceDataset newPartition = new AceDataset();
            newPartitions.put(key, newPartition);
          }
        }
      }
      this.partitions.clear();
      this.partitions.putAll(newPartitions);
    } catch (IOException ex) {
      LOG.error(ex.getMessage());
    }
    return this;
  }

  public void syncLinkage(Path linkageFile) {
  }

  private String bySizeKey(String prefix, int partitionCount, int maxSize, int totalEx) {
    StringBuilder sb = new StringBuilder();
    int leftBound = ((partitionCount-1)*maxSize)+1;
    int rightBound = partitionCount*maxSize;
    if (! prefix.equals("")) {
      sb.append(prefix);
      sb.append(File.separator);
    }
    sb.append(leftBound);
    if (leftBound != totalEx) {
      sb.append("-");
      if (rightBound > totalEx) {
        sb.append(totalEx);
      } else {
        sb.append(rightBound);
      }
    }
    return sb.toString();
  }
}
