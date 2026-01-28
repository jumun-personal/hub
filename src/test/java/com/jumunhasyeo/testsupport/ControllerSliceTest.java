package com.jumunhasyeo.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.company.application.CompanyService;
import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.product.application.ProductService;
import com.jumunhasyeo.stock.application.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerSliceSupport
public abstract class ControllerSliceTest {

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

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
