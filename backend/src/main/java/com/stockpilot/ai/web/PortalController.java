package com.stockpilot.ai.web;

import com.stockpilot.ai.service.PortalService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portal")
@PreAuthorize("hasRole('CUSTOMER_USER')")
public class PortalController {
    private final PortalService portal;

    public PortalController(PortalService portal) {
        this.portal = portal;
    }

    @GetMapping("/products")
    public List<ApiDtos.PortalProduct> products() {
        return portal.products();
    }

    @GetMapping("/price-list")
    public List<ApiDtos.PortalProduct> priceList() {
        return portal.priceList();
    }

    @PostMapping("/orders")
    public ApiDtos.PortalOrderResponse placeOrder(@Valid @RequestBody ApiDtos.PortalOrderRequest request) {
        return portal.placeOrder(request);
    }

    @GetMapping("/orders")
    public List<ApiDtos.PortalOrderResponse> orders() {
        return portal.orders();
    }

    @GetMapping("/outstanding")
    public Map<String, Object> outstanding() {
        return portal.outstanding();
    }

    @GetMapping("/invoices")
    public List<ApiDtos.PortalInvoice> invoices() {
        return portal.invoices();
    }
}
