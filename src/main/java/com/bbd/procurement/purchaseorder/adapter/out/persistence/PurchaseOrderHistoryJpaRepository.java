package com.bbd.procurement.purchaseorder.adapter.out.persistence;

import com.bbd.procurement.purchaseorder.domain.PurchaseOrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderHistoryJpaRepository extends JpaRepository<PurchaseOrderHistory, Long> {

    List<PurchaseOrderHistory> findByPoNumberOrderByChangedAtAsc(String poNumber);

}
