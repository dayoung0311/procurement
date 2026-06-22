package com.bbd.procurement.purchaseorder.adapter.in.web.response;

import com.bbd.procurement.purchaseorder.domain.PurchaseRequestStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PurchaseRequestNotificationResponse(
        String eventId,
        String soNumber,
        String warehouseCode,
        PurchaseRequestStatus status,
        LocalDateTime receivedAt,
        List<LineResponse> lines
) {
    public record LineResponse(
            String sku,
            int quantity
    ) {

    }
}
