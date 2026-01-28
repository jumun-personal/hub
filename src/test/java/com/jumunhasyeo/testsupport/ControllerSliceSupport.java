package com.jumunhasyeo.testsupport;

import com.jumunhasyeo.ControllerTestConfig;
import com.jumunhasyeo.common.exception.GlobalExceptionHandler;
import com.jumunhasyeo.company.presentation.CompanyInternalWebController;
import com.jumunhasyeo.company.presentation.CompanyWebController;
import com.jumunhasyeo.hub.hub.presentation.HubInternalWebController;
import com.jumunhasyeo.hub.hub.presentation.HubWebController;
import com.jumunhasyeo.hub.hubRoute.presentation.HubRouteInternalWebController;
import com.jumunhasyeo.product.presentation.ProductController;
import com.jumunhasyeo.stock.presentation.StockInternalWebController;
import com.jumunhasyeo.stock.presentation.StockWebController;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(controllers = {
        CompanyWebController.class,
        CompanyInternalWebController.class,
        HubWebController.class,
        HubInternalWebController.class,
        HubRouteInternalWebController.class,
        ProductController.class,
        StockWebController.class,
        StockInternalWebController.class
})
@ActiveProfiles("test")
@Import({ControllerTestConfig.class, GlobalExceptionHandler.class})
public @interface ControllerSliceSupport {
}
