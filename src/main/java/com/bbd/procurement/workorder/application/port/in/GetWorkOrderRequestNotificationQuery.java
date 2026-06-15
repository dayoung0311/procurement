package com.bbd.procurement.workorder.application.port.in;

import com.bbd.procurement.workorder.domain.WorkOrederRequestNotification;

import java.util.List;

public interface GetWorkOrderRequestNotificationQuery {

    List<WorkOrederRequestNotification> list();

}
