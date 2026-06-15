package com.bbd.procurement.workorder.application.port.out;

import com.bbd.procurement.workorder.domain.WorkOrederRequestNotification;

public interface SaveWorkOrderRequestNotificationPort {

    void save(WorkOrederRequestNotification notification);

}
