package com.bbd.procurement.vendor.application.service;

import com.bbd.procurement.global.error.ApiException;
import com.bbd.procurement.global.error.ErrorCode;
import com.bbd.procurement.vendor.application.port.in.command.RegisterVendorCommand;
import com.bbd.procurement.vendor.application.port.out.LoadVendorPort;
import com.bbd.procurement.vendor.application.port.out.SaveVendorPort;
import com.bbd.procurement.vendor.application.port.out.VendorCodeGeneratorPort;
import com.bbd.procurement.vendor.domain.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VendorService.register() 단위테스트 (이슈 #67).
 *
 * 리팩터링 내용 검증:
 *  - nextval 채번 직후의 무의미한 existsByCode 사전 검사 제거 (정상 성공 경로 유지)
 *  - 저장 시 uk_vendor_code 제약 위반(DataIntegrityViolationException)을
 *    도메인 오류 VENDOR_CODE_DUPLICATED(409)로 변환
 */
@ExtendWith(MockitoExtension.class)
class VendorServiceRegisterTest {

    @Mock SaveVendorPort saveVendorPort;
    @Mock LoadVendorPort loadVendorPort;
    @Mock VendorCodeGeneratorPort vendorCodeGeneratorPort;

    @InjectMocks VendorService sut;

    private static final String CODE = "V000001";

    @Test
    @DisplayName("정상 등록: 채번된 코드로 Vendor를 생성해 저장하고 결과를 반환한다")
    void register_success() {
        RegisterVendorCommand command =
                new RegisterVendorCommand("ACME", "010-0000-0000", "NET30");
        when(vendorCodeGeneratorPort.generate()).thenReturn(CODE);
        Vendor saved = Vendor.create(CODE, "ACME", "010-0000-0000", "NET30");
        when(saveVendorPort.save(any(Vendor.class))).thenReturn(saved);

        Vendor result = sut.register(command);

        assertThat(result).isSameAs(saved);
        verify(saveVendorPort).save(any(Vendor.class));
        // 죽은 사전 검사가 제거되었으므로 existsByCode 류의 조회는 호출되지 않는다.
        verify(loadVendorPort, never()).findByCode(any());
    }

    @Test
    @DisplayName("제약 위반: 저장 시 DataIntegrityViolationException은 VENDOR_CODE_DUPLICATED로 변환된다")
    void register_duplicateConstraint_isConvertedToApiException() {
        RegisterVendorCommand command =
                new RegisterVendorCommand("ACME", "010-0000-0000", "NET30");
        when(vendorCodeGeneratorPort.generate()).thenReturn(CODE);
        when(saveVendorPort.save(any(Vendor.class)))
                .thenThrow(new DataIntegrityViolationException("uk_vendor_code"));

        assertThatThrownBy(() -> sut.register(command))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VENDOR_CODE_DUPLICATED);
    }
}
