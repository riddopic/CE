package com.tmobile.pacbot.gcp.inventory.collector;

import com.google.cloud.compute.v1.*;
import com.tmobile.pacbot.gcp.inventory.vo.VMDiskVH;
import com.tmobile.pacbot.gcp.inventory.vo.VirtualMachineVH;
import org.slf4j.LoggerFactory;
import com.tmobile.pacbot.gcp.inventory.auth.GCPCredentialsProvider;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class VMInventoryCollector {

    @Autowired
    GCPCredentialsProvider gcpCredentialsProvider;

    private static final Logger logger = LoggerFactory.getLogger(VMInventoryCollector.class);

    public List<VirtualMachineVH> fetchInstanceInventory(String projectId) throws IOException {
        List<VirtualMachineVH> instanceList = new ArrayList<>();
        InstancesClient instancesClient = gcpCredentialsProvider.getInstancesClient();

        AggregatedListInstancesRequest aggregatedListInstancesRequest = AggregatedListInstancesRequest
                .newBuilder()
                .setProject(projectId)
                .build();

        InstancesClient.AggregatedListPagedResponse response = instancesClient
                .aggregatedList(aggregatedListInstancesRequest);

        for (Map.Entry<String, InstancesScopedList> zoneInstances : response.iterateAll()) {
            // Instances scoped by each zone
            String zone = zoneInstances.getKey();
            if (!zoneInstances.getValue().getInstancesList().isEmpty()) {
                // zoneInstances.getKey() returns the fully qualified address.
                // Hence, strip it to get the zone name only
                String zoneName = zone.substring(zone.lastIndexOf("/") + 1);
                logger.debug("Instances at %s: {} ", zoneName);
                for (Instance instance : zoneInstances.getValue().getInstancesList()) {
                    try {
                        logger.debug((instance.getName() + " " + instance.getCreationTimestamp()));
                        VirtualMachineVH virtualMachineVH = new VirtualMachineVH();
                        virtualMachineVH.setId(String.valueOf(instance.getId()));
                        virtualMachineVH.setMachineType(instance.getMachineType());
                        virtualMachineVH.setTags(instance.getLabelsMap());
                        virtualMachineVH.setProjectName(projectId);
                        virtualMachineVH.setName(instance.getName());
                        virtualMachineVH.setDescription(instance.getDescription());
                        virtualMachineVH.setZone(zoneName);
                        virtualMachineVH.setStatus(instance.getStatus());
                        this.setVMDisks(instance, virtualMachineVH);
                        instanceList.add(virtualMachineVH);
                    } catch (Exception e) {
                        logger.error("Error while fetching instance inventory for {} {}", instance.getName(), e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        return instanceList;
    }

    private void setVMDisks(Instance vmInstance, VirtualMachineVH vm) {
        List<VMDiskVH> diskVHS = new ArrayList<>();
        List<AttachedDisk> disksList = vmInstance.getDisksList();
        // convert AttachedDisk into VMDiskVH
        disksList.forEach(disk -> {
            VMDiskVH diskVH = new VMDiskVH();
            diskVH.setId(String.valueOf(disk.getIndex()));
            diskVH.setName(disk.getDeviceName());
            diskVH.setSizeInGB(disk.getDiskSizeGb());
            diskVH.setType(disk.getType());
            diskVHS.add(diskVH);
        });
        vm.setDisks(diskVHS);
    }
}