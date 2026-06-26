package io.batchadmin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the opt-in OAuth2/OIDC security layer: the REST API becomes a JWT resource server (401
 * without a token, 200 with one) and the GUI redirects unauthenticated users into the OIDC login
 * flow, all scoped to the component's own paths.
 */
@SpringBootTest(classes = TestBatchApplication.class)
@AutoConfigureMockMvc
@Import(BatchAdminSecurityTest.SecurityStubs.class)
@TestPropertySource(properties = {
        "batch.admin.security.enabled=true",
        // Drives the resource-server chain condition; the stub decoder below stands in for real
        // issuer discovery so no network call is made.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example/realms/batch"
})
class BatchAdminSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiRejectsAnonymousRequestsWith401() throws Exception {
        mvc.perform(get("/batch-admin/api/jobs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiAcceptsAValidBearerToken() throws Exception {
        mvc.perform(get("/batch-admin/api/jobs").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void guiRedirectsAnonymousUsersToOidcLogin() throws Exception {
        mvc.perform(get("/batch-admin"))
                .andExpect(status().is3xxRedirection());
    }

    @Configuration(proxyBeanMethods = false)
    static class SecurityStubs {

        /** Stub decoder so the resource-server chain wires up without contacting a real issuer. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("stub decoder is never invoked in tests");
            };
        }

        /** Single OIDC client registration so the GUI chain can drive the login redirect. */
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("test")
                    .clientId("batch-admin")
                    .clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("https://issuer.example/authorize")
                    .tokenUri("https://issuer.example/token")
                    .scope("openid")
                    .clientName("Test IdP")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
