package com.jumunhasyeo.hub.hub.infrastructure.init.dto;

import lombok.Data;

@Data
public class HubData {
    private String name;
    private String street;
    private Double latitude;
    private Double longitude;
    private String type;
    private String center;
}
