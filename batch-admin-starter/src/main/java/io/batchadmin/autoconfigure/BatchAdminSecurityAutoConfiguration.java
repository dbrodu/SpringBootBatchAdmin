package io.batchadmin.autoconfigure;

import io.batchadmin.autoconfigure.BatchAdminProperties.Security;
import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.util.StringUtils;

/**
 * Optional OAuth2/OIDC security for the Batch Admin component, activated with
 * {@code batch.admin.security.enabled=true}.
 *
 * <p>It installs up to two filter chains <i>scoped to the component's own paths</i>, so it never
 * takes over a host application's existing security on the rest of the app:</p>
 * <ol>
 *   <li>a stateless <b>resource-server</b> chain for the REST API ({@code <basePath>/api/**}),
 *       validating bearer JWTs — active when the standard
 *       {@code spring.security.oauth2.resourceserver.jwt.*} properties are set (or a custom
 *       {@link JwtDecoder} bean is provided);</li>
 *   <li>an interactive <b>OIDC login</b> chain for the GUI ({@code <basePath>/**} plus the OAuth2
 *       login endpoints) — active when a {@link ClientRegistrationRepository} is present, i.e. the
 *       standard {@code spring.security.oauth2.client.registration.*} properties are set.</li>
 * </ol>
 *
 * <p>The two chains are independent: an API-only (resource server) or GUI-only (login) deployment
 * works without the other being configured. Defining the chains here makes Spring Boot's own
 * catch-all security chains back off, so the rest of the application is left to the host.</p>
 *
 * <p>Ordering: declared <i>before</i> {@link SecurityAutoConfiguration},
 * {@link OAuth2ClientAutoConfiguration} and {@link OAuth2ResourceServerAutoConfiguration} so their
 * whole-application chains (all {@code @ConditionalOnDefaultWebSecurity}) back off in favour of
 * these scoped ones. Because the JWT decoder / client registry beans are therefore not yet defined
 * when these chains' conditions are evaluated, activation is driven by the standard
 * {@code spring.security.oauth2.*} <i>properties</i> (or a host-supplied bean); the beans
 * themselves are resolved later, when each chain is actually built.</p>
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, OAuth2ClientAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, EnableWebSecurity.class})
@ConditionalOnProperty(prefix = "batch.admin.security", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BatchAdminProperties.class)
@EnableWebSecurity
public class BatchAdminSecurityAutoConfiguration {

    /**
     * Resource-server chain for the REST API. Highest precedence so it claims {@code /api/**} before
     * the GUI chain; stateless and never redirects (returns 401 for a missing/invalid token).
     */
    @Bean
    @Order(1)
    @Conditional(ResourceServerConfigured.class)
    public SecurityFilterChain batchAdminApiSecurityFilterChain(HttpSecurity http,
                                                                BatchAdminProperties properties) throws Exception {
        Security security = properties.getSecurity();

        http.securityMatcher(properties.getApiPath() + "/**")
                .authorizeHttpRequests(auth -> {
                    if (StringUtils.hasText(security.getApiAuthority())) {
                        auth.anyRequest().hasAuthority(security.getApiAuthority());
                    } else {
                        auth.anyRequest().authenticated();
                    }
                })
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }));
        return http.build();
    }

    /**
     * OIDC login chain for the GUI. Lower precedence; scoped to the component base path plus the
     * OAuth2 login endpoints ({@code /oauth2/**}, {@code /login/**}) so the redirect/callback dance
     * works without taking over the rest of the application.
     */
    @Bean
    @Order(2)
    @Conditional(ClientConfigured.class)
    public SecurityFilterChain batchAdminUiSecurityFilterChain(HttpSecurity http,
                                                               BatchAdminProperties properties) throws Exception {
        Security security = properties.getSecurity();

        http.securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher(properties.getBasePath() + "/**"),
                        new AntPathRequestMatcher("/oauth2/**"),
                        new AntPathRequestMatcher("/login/**")))
                .authorizeHttpRequests(auth -> {
                    if (StringUtils.hasText(security.getUiAuthority())) {
                        auth.anyRequest().hasAuthority(security.getUiAuthority());
                    } else {
                        auth.anyRequest().authenticated();
                    }
                })
                // The GUI performs state-changing POSTs (create/launch/schedule jobs) from server
                // rendered forms; CSRF protection is kept on for this interactive chain.
                .oauth2Login(login -> {
                });
        return http.build();
    }

    /**
     * The REST API chain activates when a JWT resource server is configured — either through the
     * standard {@code issuer-uri}/{@code jwk-set-uri} properties, or by a host-provided
     * {@link JwtDecoder} bean.
     */
    static final class ResourceServerConfigured extends AnyNestedCondition {

        ResourceServerConfigured() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "issuer-uri")
        static class IssuerUri {
        }

        @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "jwk-set-uri")
        static class JwkSetUri {
        }

        @ConditionalOnBean(JwtDecoder.class)
        static class DecoderBean {
        }
    }

    /**
     * The GUI chain activates when an OIDC client is configured — either through the standard
     * {@code spring.security.oauth2.client.registration.*} properties (the production path; checked
     * directly because this auto-config runs before the client is registered as a bean), or by a
     * host/test-supplied {@link ClientRegistrationRepository} bean.
     */
    static final class ClientConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, Object> registrations = Binder.get(context.getEnvironment())
                    .bind("spring.security.oauth2.client.registration",
                            Bindable.mapOf(String.class, Object.class))
                    .orElse(Collections.emptyMap());
            if (!registrations.isEmpty()) {
                return true;
            }
            ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
            return beanFactory != null
                    && beanFactory.getBeanNamesForType(ClientRegistrationRepository.class, false, false).length > 0;
        }
    }
}
