package com.bbd.procurement.purchaseorder.application.port.in;

import com.bbd.procurement.purchaseorder.domain.PurchaseOrderHistory;

import java.util.List;

public interface GetPurchaseOrderHistoryQuery {

    List<PurchaseOrderHistory> getHistory(String poNumber);

}
