package com.bbd.procurement.purchaseorder.application.service;

import com.bbd.procurement.global.error.ApiException;
import com.bbd.procurement.global.error.ErrorCode;
import com.bbd.procurement.purchaseorder.adapter.out.external.SalesOrderResponse;
import com.bbd.procurement.purchaseorder.adapter.out.external.SalesRestClient;
import com.bbd.procurement.purchaseorder.application.port.in.GetSalesOrderQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
@RequiredArgsConstructor
public class SalesOrderQueryService implements GetSalesOrderQuery {

    private final SalesRestClient salesRestClient;

    @Override
    public SalesOrderResponse getBySoNumber(String soNumber) {
        try {
            return salesRestClient.getSalesOrder(soNumber);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(ErrorCode.SO_NOT_FOUND);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.SALES_SERVICE_ERROR);
        }
    }
}
