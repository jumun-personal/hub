package com.jumunhasyeo.stock.infrastructure.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.*;

@Service
@RequiredArgsConstructor
public class OrderAclService {

    public Optional<String> convert(String eventType) {
        if (eventType == null) {
            return Optional.empty();
        }

        return switch (eventType) {
            case "ORDER_ROLLEDBACK" -> Optional.of(ORDER_ROLLED_BACK_EVENT.getEventName());
            case "ORDER_CANCELLED" -> Optional.of(ORDER_CANCEL_EVENT.getEventName());
            case "ORDER_CREATED" -> Optional.of(ORDER_CREATED.getEventName());
            default -> Optional.empty();
        };
    }
}
