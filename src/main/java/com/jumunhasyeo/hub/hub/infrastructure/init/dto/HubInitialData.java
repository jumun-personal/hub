package com.jumunhasyeo.hub.hub.infrastructure.init.dto;

import lombok.Data;

import java.util.List;

@Data
public class HubInitialData {
    private List<HubData> centerHubs;
    private List<HubData> branchHubs;
}
