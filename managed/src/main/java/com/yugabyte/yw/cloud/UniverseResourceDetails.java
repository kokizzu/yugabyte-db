// Copyright (c) YugaByte, Inc.

package com.yugabyte.yw.cloud;

import com.yugabyte.yw.commissioner.Common;
import com.yugabyte.yw.forms.UniverseDefinitionTaskParams;
import com.yugabyte.yw.forms.UniverseDefinitionTaskParams.Cluster;
import com.yugabyte.yw.forms.UniverseDefinitionTaskParams.UserIntent;
import com.yugabyte.yw.models.InstanceType;
import com.yugabyte.yw.models.PriceComponent;
import com.yugabyte.yw.models.Provider;
import com.yugabyte.yw.models.Region;
import com.yugabyte.yw.models.helpers.DeviceInfo;
import com.yugabyte.yw.models.helpers.NodeDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

import static com.yugabyte.yw.cloud.PublicCloudConstants.GP2_SIZE;
import static com.yugabyte.yw.cloud.PublicCloudConstants.IO1_PIOPS;
import static com.yugabyte.yw.cloud.PublicCloudConstants.IO1_SIZE;

public class UniverseResourceDetails {
  public static final Logger LOG = LoggerFactory.getLogger(UniverseResourceDetails.class);

  public double pricePerHour = 0;
  public double ebsPricePerHour = 0;
  public int numCores = 0;
  public double memSizeGB = 0;
  public int volumeCount = 0;
  public int volumeSizeGB = 0;
  public int numNodes = 0;
  public HashSet<String> azList = new HashSet<>();

  public void addCostPerHour(double price) {
    pricePerHour += price;
  }

  public void addEBSCostPerHour(double ebsPrice) {
    ebsPricePerHour += ebsPrice;
  }

  public void addVolumeCount(double volCount) {
    volumeCount += volCount;
  }

  public void addMemSizeGB(double memSize) {
    memSizeGB += memSize;
  }

  public void addVolumeSizeGB(double volSize) {
    volumeSizeGB += volSize;
  }

  public void addAz(String azName) {
    azList.add(azName);
  }

  public void addNumCores(int cores) {
    numCores += cores;
  }

  public void addNumNodes(int numNodes) {
    this.numNodes += numNodes;
  }

  public void addPrice(UniverseDefinitionTaskParams params) {

    // Calculate price
    double hourlyPrice = 0.0;
    double hourlyEBSPrice = 0.0;
    for (NodeDetails nodeDetails : params.nodeDetailsSet) {
      UserIntent userIntent = params.retrieveClusterByUuid(nodeDetails.clusterUuid).userIntent;
      Provider provider = Provider.get(UUID.fromString(userIntent.provider));

      if (!nodeDetails.isActive()) {
        continue;
      }
      Region region = Region.getByCode(provider, nodeDetails.cloudInfo.region);

      // Add price of instance, using spotPrice if this universe is a spotPrice-based universe.
      if (userIntent.providerType.equals(Common.CloudType.aws) && userIntent.spotPrice > 0.0) {
        hourlyPrice += userIntent.spotPrice;
      } else {
        PriceComponent instancePrice = PriceComponent.get(provider.code, region.code,
                                                          userIntent.instanceType);
        if (instancePrice == null) {
          continue;
        }
        hourlyPrice += instancePrice.priceDetails.pricePerHour;
      }

      // Add price of volumes if necessary
      // TODO: Remove aws check once GCP volumes are decoupled from "EBS" designation
      if (userIntent.deviceInfo.ebsType != null &&
          params.retrievePrimaryCluster().userIntent.providerType.equals(Common.CloudType.aws)) {
        Integer numVolumes = userIntent.deviceInfo.numVolumes;
        Integer diskIops = userIntent.deviceInfo.diskIops;
        Integer volumeSize = userIntent.deviceInfo.volumeSize;
        PriceComponent sizePrice;
        switch (userIntent.deviceInfo.ebsType) {
          case IO1:
            PriceComponent piopsPrice = PriceComponent.get(provider.code, region.code, IO1_PIOPS);
            sizePrice = PriceComponent.get(provider.code, region.code, IO1_SIZE);
            if (piopsPrice != null && sizePrice != null) {
              hourlyEBSPrice += (numVolumes * (diskIops * piopsPrice.priceDetails.pricePerHour));
              hourlyEBSPrice += (numVolumes * (volumeSize * sizePrice.priceDetails.pricePerHour));
            }
            break;
          case GP2:
            sizePrice = PriceComponent.get(provider.code, region.code, GP2_SIZE);
            if (sizePrice != null) {
              hourlyEBSPrice += (numVolumes * volumeSize * sizePrice.priceDetails.pricePerHour);
            }
            break;
          default:
            break;
        }
      }
    }
    hourlyPrice += hourlyEBSPrice;

    // Add price to details
    addCostPerHour(Double.parseDouble(String.format("%.4f", hourlyPrice)));
    addEBSCostPerHour(Double.parseDouble(String.format("%.4f", hourlyEBSPrice)));
  }

  /**
   * Create a UniverseResourceDetails object, which contains info on the various pricing and
   * other sorts of resources used by this universe.
   *
   * @param nodes Nodes that make up this universe.
   * @param params Parameters describing this universe.
   * @return a UniverseResourceDetails object containing info on the universe's resources.
   */
  public static UniverseResourceDetails create(Collection<NodeDetails> nodes,
                                               UniverseDefinitionTaskParams params) {
    UniverseResourceDetails details = new UniverseResourceDetails();
    for (Cluster cluster : params.clusters) {
      details.addNumNodes(cluster.userIntent.numNodes);
    }
    for (NodeDetails node : nodes) {
      if (node.isActive()) {
        UserIntent userIntent = params.retrieveClusterByUuid(node.clusterUuid).userIntent;
        details.addVolumeCount(userIntent.deviceInfo.numVolumes);
        details.addVolumeSizeGB(userIntent.deviceInfo.volumeSize * userIntent.deviceInfo.numVolumes);
        details.addAz(node.cloudInfo.az);
        InstanceType instanceType = InstanceType.get(userIntent.providerType,
            node.cloudInfo.instance_type);
        if (instanceType == null) {
          LOG.error("Couldn't find instance type " + node.cloudInfo.instance_type +
              " for provider " + userIntent.providerType);
        } else {
          details.addMemSizeGB(instanceType.memSizeGB);
          details.addNumCores(instanceType.numCores);
        }
      }
    }
    details.addPrice(params);
    return details;
  }
}
