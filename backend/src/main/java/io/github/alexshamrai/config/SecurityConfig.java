package io.github.alexshamrai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Single-user HTTP Basic protection for the whole app — the Cloud Run URL is public, so
 * every path (API, UI, Swagger, H2 console) requires the env-configured credentials.
 * Stateless: no sessions.
 *
 * <p>CSRF is disabled but NOT because Basic auth is immune to it — browsers cache Basic
 * credentials per-origin and auto-attach them to any request to that origin regardless of
 * which page initiated it, the same ambient-authority property cookie-based CSRF defenses
 * exist for. {@link RequireXhrHeaderFilter} closes that gap for state-changing requests
 * without needing token issuance/refresh machinery on top of stateless Basic auth.
 *
 * <p>Decision from task-list.md: ALL paths authenticated, no exemptions. Cloud Run
 * health checks use TCP, not HTTP, so no health-path exemption is needed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new RequireXhrHeaderFilter(), BasicAuthenticationFilter.class)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${music-cat.auth.username}") String username,
            @Value("${music-cat.auth.password}") String password,
            Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("cloud"))
                && "admin".equals(username) && "admin".equals(password)) {
            throw new IllegalStateException(
                    "Refusing to start: the cloud profile is active but MUSIC_CAT_USER/"
                            + "MUSIC_CAT_PASSWORD are still the checked-in admin/admin defaults, "
                            + "which would serve the public Cloud Run URL behind trivially-guessable "
                            + "credentials. Set both environment variables.");
        }
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserDetails user = User.withUsername(username)
                .password(encoder.encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
