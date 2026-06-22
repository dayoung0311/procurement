package com.bbd.procurement.purchaseorder.adapter.in.web.response;

import com.bbd.procurement.global.error.ApiException;
import com.bbd.procurement.global.error.ErrorCode;
import com.bbd.procurement.purchaseorder.adapter.in.messaging.event.PurchaseRequested;
import com.bbd.procurement.purchaseorder.domain.PurchaseOrderHistory;
import com.bbd.procurement.purchaseorder.domain.PurchaseRequestNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * JSON payload -> 응답 record 변환 책임을 웹 응답 record 밖으로 분리한 어셈블러.
 * 역직렬화 실패(JacksonException)는 ApiException(INTERNAL_ERROR)으로 감싸
 * GlobalExceptionHandler가 일관된 ProblemDetail(C999, 500)로 응답하게 한다.
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderResponseAssembler {

    private final ObjectMapper objectMapper;

    public PurchaseOrderHistoryResponse toHistoryResponse(PurchaseOrderHistory history) {
        try {
            JsonNode before = history.getBeforePayload() == null
                    ? null
                    : objectMapper.readTree(history.getBeforePayload());
            JsonNode after = objectMapper.readTree(history.getAfterPayload());
            return new PurchaseOrderHistoryResponse(
                    history.getChangeType(),
                    history.getChangedBy(),
                    history.getChangedAt(),
                    before,
                    after
            );
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public PurchaseRequestNotificationResponse toNotificationResponse(PurchaseRequestNotification notification) {
        try {
            PurchaseRequested event =
                    objectMapper.readValue(notification.getPayload(), PurchaseRequested.class);
            List<PurchaseRequestNotificationResponse.LineResponse> lines = event.lines().stream()
                    .map(line -> new PurchaseRequestNotificationResponse.LineResponse(
                            line.sku(), line.quantity()))
                    .toList();
            return new PurchaseRequestNotificationResponse(
                    notification.getEventId(),
                    notification.getSoNumber(),
                    notification.getWarehouseCode(),
                    notification.getStatus(),
                    notification.getReceivedAt(),
                    lines
            );
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
