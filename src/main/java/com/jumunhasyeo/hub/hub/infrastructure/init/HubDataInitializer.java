package com.jumunhasyeo.hub.hub.infrastructure.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jumunhasyeo.hub.hub.infrastructure.init.dto.HubData;
import com.jumunhasyeo.hub.hub.infrastructure.init.dto.HubInitialData;
import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class HubDataInitializer implements ApplicationRunner {

    @Value("classpath:hub-initial-data.yml")
    private Resource hubDataFile;

    private final HubService hubService;
    private final HubRepository hubRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 이미 데이터가 있으면 초기화하지 않음
        if (hubRepository.count() > 0) {
            log.info("Hub data already exists. Skipping initialization.");
            return;
        }

        log.info("Starting hub data initialization...");
        HubInitialData hubInitialData = loadHubData();

        // Center Hub과 Branch Hub의 ID를 매핑하기 위한 Map
        Map<String, UUID> hubNameToIdMap = new HashMap<>();

        // 1단계: Center Hub 생성
        for (HubData hubData : hubInitialData.getCenterHubs()) {
            UUID hubId = createCenterHub(hubData);
            hubNameToIdMap.put(hubData.getName(), hubId);
            log.info("Created center hub: {} (ID: {})", hubData.getName(), hubId);
        }

        // 2단계: Branch Hub 생성
        for (HubData hubData : hubInitialData.getBranchHubs()) {
            UUID centerHubId = hubNameToIdMap.get(hubData.getCenter());
            if (centerHubId == null) {
                continue;
            }

            UUID hubId = createBranchHub(hubData, centerHubId);
            hubNameToIdMap.put(hubData.getName(), hubId);
        }

        log.info("Hub data initialization completed. Total hubs created: {}", hubNameToIdMap.size());
    }

    private HubInitialData loadHubData() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(hubDataFile.getInputStream(), HubInitialData.class);
    }

    private UUID createCenterHub(HubData hubData) {
        CreateHubCommand command = CreateHubCommand.createCenter(
                hubData.getName(),
                hubData.getStreet(),
                hubData.getLatitude(),
                hubData.getLongitude(),
                HubType.CENTER
        );

        return hubService.create(command).id();
    }

    private UUID createBranchHub(HubData hubData, UUID centerHubId) {
        CreateHubCommand command = CreateHubCommand.createBranch(
                centerHubId,
                hubData.getName(),
                hubData.getStreet(),
                hubData.getLatitude(),
                hubData.getLongitude(),
                HubType.BRANCH
        );

        return hubService.create(command).id();
    }
}
