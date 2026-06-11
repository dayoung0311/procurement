package com.bbd.procurement.purchaseorder.application.port.in;

import com.bbd.procurement.purchaseorder.adapter.out.external.SalesOrderResponse;

public interface GetSalesOrderQuery {

    SalesOrderResponse getBySoNumber(String soNumber);

}
