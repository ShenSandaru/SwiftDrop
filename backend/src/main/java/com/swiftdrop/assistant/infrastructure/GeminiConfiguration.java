package com.swiftdrop.assistant.infrastructure;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.swiftdrop.assistant.application.AssistantProperties;
import com.swiftdrop.assistant.application.LlmClient;

/** Wires the Gemini client with the configured base URL and timeouts. */
@Configuration(proxyBeanMethods = false)
class GeminiConfiguration {

    @Bean
    LlmClient llmClient(AssistantProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new GeminiLlmClient(restClient, properties);
    }
}
