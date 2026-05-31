package com.apiscope.sample.service;

import com.apiscope.sample.entity.SalesOrder;
import com.apiscope.sample.repository.dao.ProductCatalogDao;
import com.apiscope.sample.repository.dao.SalesOrderDao;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Order service — orchestrates the full order placement workflow.
 * This is the primary SERVICE that the Flow Tracer will show at the top of the
 * execution diagram, with InventoryService and PaymentService nested beneath it.
 *
 * <p>Call chain visible in Flow Tracer:
 * <pre>
 * CONTROLLER  OrderFlowController.checkout()
 *   SERVICE   OrderService.validateOrder()
 *   SERVICE   InventoryService.checkAvailability()
 *     REPO    InventoryRepository.findStockByProductId()
 *   SERVICE   InventoryService.reserveInventory()
 *     REPO    InventoryRepository.reserveStock()
 *   SERVICE   PaymentService.validatePayment()
 *     REPO    PaymentRepository.validatePaymentMethod()
 *   SERVICE   PaymentService.authorisePayment()
 *     REPO    PaymentRepository.createPaymentRecord()
 *   SERVICE   OrderService.confirmOrder()
 * </pre>
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final PaymentService   paymentService;
    private final SalesOrderDao    salesOrderDao;
    private final ProductCatalogDao productCatalogDao;

    public OrderService(InventoryService inventoryService, PaymentService paymentService,
                        SalesOrderDao salesOrderDao, ProductCatalogDao productCatalogDao) {
        this.inventoryService   = inventoryService;
        this.paymentService     = paymentService;
        this.salesOrderDao      = salesOrderDao;
        this.productCatalogDao  = productCatalogDao;
    }

    /**
     * Validate the incoming order request — checks required fields.
     *
     * @param request the raw order request map
     * @return sanitised order details
     * @throws IllegalArgumentException if required fields are missing
     */
    public Map<String, Object> validateOrder(Map<String, Object> request) {
        String productId      = (String) request.get("productId");
        String paymentMethodId= (String) request.get("paymentMethodId");
        Object quantityObj    = request.get("quantity");

        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            throw new IllegalArgumentException("paymentMethodId is required");
        }

        int quantity = quantityObj instanceof Number n ? n.intValue() : 1;
        double price = request.get("unitPrice") instanceof Number p ? p.doubleValue() : 29.99;

        return Map.of(
                "productId",       productId,
                "quantity",        quantity,
                "unitPrice",       price,
                "total",           quantity * price,
                "paymentMethodId", paymentMethodId
        );
    }

    /**
     * Full checkout pipeline — orchestrates inventory, payment, and order creation.
     *
     * @param request the order placement request
     * @return the confirmed order with payment and reservation details
     */
    public Map<String, Object> checkout(Map<String, Object> request) {
        // Step 1 — validate
        Map<String, Object> validated = validateOrder(request);

        String productId       = (String) validated.get("productId");
        String paymentMethodId = (String) validated.get("paymentMethodId");
        int    quantity        = (int)    validated.get("quantity");
        double total           = (double) validated.get("total");
        String orderId         = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Step 2 — check &amp; reserve inventory
        inventoryService.checkAvailability(productId, quantity);
        Map<String, Object> reservation = inventoryService.reserveInventory(productId, quantity);

        // Step 3 — validate &amp; authorise payment
        paymentService.validatePayment(paymentMethodId);
        Map<String, Object> payment = paymentService.authorisePayment(orderId, total, paymentMethodId);

        // Step 4 — confirm
        return confirmOrder(orderId, validated, reservation, payment);
    }

    /**
     * Finalise and persist the order after successful payment authorisation.
     *
     * @param orderId     the generated order ID
     * @param orderDetails validated order line items
     * @param reservation  inventory reservation confirmation
     * @param payment      payment authorisation result
     * @return complete confirmed order record
     */
    public Map<String, Object> confirmOrder(
            String orderId,
            Map<String, Object> orderDetails,
            Map<String, Object> reservation,
            Map<String, Object> payment) {

        // Resolve product ID from SKU for the FK column
        String productSku = (String) orderDetails.get("productId");
        Long   productId  = productCatalogDao.findBySku(productSku)
                .map(p -> p.getId()).orElse(null);

        // Persist the confirmed order to the database
        SalesOrder order = new SalesOrder();
        order.setOrderRef(orderId);
        order.setProductId(productId);
        order.setQuantity((Integer) orderDetails.get("quantity"));
        order.setUnitPrice((Double) orderDetails.get("unitPrice"));
        order.setTotalAmount((Double) orderDetails.get("total"));
        order.setStatus("CONFIRMED");
        order.setCreatedDate(LocalDate.now());
        salesOrderDao.save(order);

        return Map.of(
                "orderId",           orderId,
                "status",            "CONFIRMED",
                "productId",         productSku,
                "quantity",          orderDetails.get("quantity"),
                "total",             orderDetails.get("total"),
                "paymentId",         payment.get("paymentId"),
                "authCode",          payment.get("authCode"),
                "reservationToken",  reservation.get("reservationToken"),
                "estimatedDelivery", LocalDate.now().plusDays(3).toString()
        );
    }
}
