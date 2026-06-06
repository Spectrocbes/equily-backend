package com.equily.marketdata.infrastructure;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Local development only — disables SSL certificate validation. Required when the JVM truststore
 * doesn't include the proxy/corporate CA. NEVER active in production (profile = "local" only).
 */
@Configuration
@Profile("local")
public class LocalSslConfig {

  @Bean
  public RestClient.Builder restClientBuilder() throws Exception {
    TrustManager[] trustAll =
        new TrustManager[] {
          new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {}

            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
          }
        };

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAll, new SecureRandom());

    HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext).build();

    return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(httpClient));
  }
}
