package com.bbd.procurement.global.config;

import com.bbd.procurement.purchaseorder.adapter.out.external.ItemHttpService;
import com.bbd.procurement.purchaseorder.adapter.out.external.SalesHttpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpServiceConfig {

    @Bean
    public ItemHttpService itemHttpService(@Value("${item.base-url}") String baseUrl) {
        return proxyFactory(baseUrl).createClient(ItemHttpService.class);
    }

    @Bean
    public SalesHttpService salesHttpService(@Value("${sales.base-url}") String baseUrl) {
        return proxyFactory(baseUrl).createClient(SalesHttpService.class);
    }

    private HttpServiceProxyFactory proxyFactory(String baseUrl) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
    }
}
