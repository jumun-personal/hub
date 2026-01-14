package com.jumunhasyeo.testsupport;

import com.jumunhasyeo.company.application.CompanyService;
import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.product.application.ProductService;
import com.jumunhasyeo.stock.application.StockService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class AbstractControllerMockClusterTest {

    @MockitoBean
    protected CompanyService companyService;

    @MockitoBean
    protected HubService hubService;

    @MockitoBean
    protected StockService stockService;

    @MockitoBean
    protected HubRouteService hubRouteService;

    @MockitoBean
    protected ProductService productService;
}
