package com.bbd.procurement.workorder.application.port.out;

import com.bbd.procurement.workorder.domain.WorkOrederRequestNotification;

import java.util.List;

public interface LoadWorkOrderRequestNotificationPort {

    List<WorkOrederRequestNotification> findAllOrderByReceivedAtDesc();

}
