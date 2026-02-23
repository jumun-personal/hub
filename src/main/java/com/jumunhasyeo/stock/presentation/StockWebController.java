package com.jumunhasyeo.stock.presentation;

import com.jumunhasyeo.common.ApiRes;
import com.jumunhasyeo.stock.application.StockService;
import com.jumunhasyeo.stock.application.command.CreateStockCommand;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.DeleteStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.presentation.docs.ApiDocDeleteStock;
import com.jumunhasyeo.stock.presentation.docs.ApiDocGetStock;
import com.jumunhasyeo.stock.presentation.dto.request.CreateStockReq;
import com.jumunhasyeo.stock.presentation.dto.request.DecreaseStockReq;
import com.jumunhasyeo.stock.presentation.dto.request.DeleteStockReq;
import com.jumunhasyeo.stock.presentation.dto.request.IncrementStockReq;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Stock", description = "재고 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stocks")
public class StockWebController {

    private final StockService stockService;

    @PostMapping
    public ResponseEntity<ApiRes<StockRes>> create(
            @Parameter(description = "재고 생성 요청 정보", required = true)
            @RequestBody @Valid CreateStockReq req
    ) {
        CreateStockCommand command = new CreateStockCommand(req.hubId(), req.productId(), req.quantity());
        StockRes stockRes = stockService.create(command);
        return ResponseEntity.ok(ApiRes.success(stockRes));
    }

    //재고 단건 조회 (TODO: ALL)
    @ApiDocGetStock
    @GetMapping("/{stockId}")
    public ResponseEntity<ApiRes<StockRes>> get(
            @Parameter(description = "조회할 재고의 ID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable(name = "stockId") UUID stockId
    ) {
        StockRes stockRes = stockService.get(stockId);
        return ResponseEntity.ok(ApiRes.success(stockRes));
    }

    @PostMapping("/increment")
    public ResponseEntity<ApiRes<List<StockChangeRes>>> increment(
            @Parameter(description = "멱등키 (중복 요청 방지)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Parameter(description = "재고 증가 요청 정보", required = true)
            @RequestBody @Valid List<@Valid IncrementStockReq> productList
    ) {
        List<IncreaseStockCommand> commandList = productList
                .stream()
                .map(incrStockReq -> new IncreaseStockCommand(incrStockReq.hubId(), incrStockReq.productId(), incrStockReq.quantity()))
                .toList();

        List<StockChangeRes> stockChangeResList = stockService.increment(idempotencyKey, commandList);
        return ResponseEntity.ok(ApiRes.success(stockChangeResList));
    }

    @PostMapping("/decrement")
    public ResponseEntity<ApiRes<List<StockChangeRes>>> decrement(
            @Parameter(description = "멱등키 (중복 요청 방지)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Parameter(description = "재고 감소 요청 정보", required = true)
            @RequestBody @Valid List<@Valid DecreaseStockReq> productList
    ) {
        List<DecreaseStockCommand> commandList = productList
                .stream()
                .map(descStockReq -> new DecreaseStockCommand(descStockReq.hubId(), descStockReq.productId(), descStockReq.quantity()))
                .toList();

        List<StockChangeRes> stockChangeResList = stockService.decrement(idempotencyKey, commandList);
        return ResponseEntity.ok(ApiRes.success(stockChangeResList));
    }

    //재고 삭제 (TODO: HUB_MANAGER/MASTER, SYSTEM)
    @ApiDocDeleteStock
    @DeleteMapping
    public ResponseEntity<ApiRes<StockRes>> delete(
            @Parameter(description = "재고 삭제 요청 정보", required = true)
            @RequestBody @Valid DeleteStockReq req
    ) {
        DeleteStockCommand command = new DeleteStockCommand(req.stockId(), req.userId());
        StockRes stockRes = stockService.delete(command);
        return ResponseEntity.ok(ApiRes.success(stockRes));
    }
}
