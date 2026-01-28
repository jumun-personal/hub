package com.jumunhasyeo.stock.presentation;

import com.jumunhasyeo.common.ApiRes;
import com.jumunhasyeo.stock.application.StockService;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.command.ShippedStockCommand;
import com.jumunhasyeo.stock.application.command.StoreStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockHistoryRes;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.presentation.docs.ApiDocDecrementStock;
import com.jumunhasyeo.stock.presentation.docs.ApiDocIncrementStock;
import com.jumunhasyeo.stock.presentation.dto.request.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Internal-Stock", description = "internal 서버 재고 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/api/v1/stocks")
@Slf4j
public class StockInternalWebController {

    private final StockService stockService;

    //재고 증가 (TODO: HUB_MANAGER/MASTER, SYSTEM)
    @ApiDocIncrementStock
    @PostMapping("/increment")
    public ResponseEntity<ApiRes<Boolean>> increment(
            @Parameter(description = "멱등키 (중복 요청 방지)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Parameter(description = "재고 증가 요청 정보", required = true)
            @RequestBody @Valid List<IncrementStockReq> productList
    ) {
        List<IncreaseStockCommand> commandList = productList
                .stream()
                .map(incrStockReq -> new IncreaseStockCommand(incrStockReq.productId(), incrStockReq.quantity()))
                .toList();

        List<StockRes> stockResList = stockService.increment(idempotencyKey, commandList);
        return ResponseEntity.ok(ApiRes.success(true));
    }

    //재고 감소(TODO: HUB_MANAGER/MASTER, SYSTEM)
    @ApiDocDecrementStock
    @PostMapping("/decrement")
    public ResponseEntity<ApiRes<Boolean>> decrement(
            @Parameter(description = "멱등키 (중복 요청 방지)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Parameter(description = "재고 감소 요청 정보", required = true)
            @RequestBody @Valid List<DecreaseStockReq> productList
    ) {
        List<DecreaseStockCommand> commandList = productList
                .stream()
                .map(descStockReq -> new DecreaseStockCommand(descStockReq.productId(), descStockReq.quantity()))
                .toList();

        List<StockRes> stockRes = stockService.decrement(idempotencyKey, commandList);
        return ResponseEntity.ok(ApiRes.success(true));
    }

    @PostMapping("/store")
    public ResponseEntity<ApiRes<List<StockHistoryRes>>> store(
            @Parameter(description = "멱등키 (중복 요청 방지)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Parameter(description = "재고 입고 요청 정보", required = true)
            @RequestBody @Valid StoreStockReqList reqList
    ) {
        List<StoreStockCommand> commandList = reqList.productList()
                .stream()
                .map(req -> new StoreStockCommand(req.hubId(), req.productId(), req.quantity()))
                .toList();

        List<StockHistoryRes> stockHistoryResList = stockService.store(idempotencyKey, commandList);
        return ResponseEntity.ok(ApiRes.success(stockHistoryResList));
    }

    @PostMapping("/shipped")
    public ResponseEntity<ApiRes<List<StockHistoryRes>>> shipped(
            @Parameter(description = "멱등키 (중복 요청 방지)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Parameter(description = "재고 출고 요청 정보", required = true)
            @RequestBody @Valid ShippedStockReqList reqList
    ) {
        List<ShippedStockCommand> commandList = reqList.productList()
                .stream()
                .map(req -> new ShippedStockCommand(req.hubId(), req.productId(), req.quantity()))
                .toList();

        List<StockHistoryRes> stockHistoryResList = stockService.shipped(idempotencyKey, commandList);
        return ResponseEntity.ok(ApiRes.success(stockHistoryResList));
    }
}