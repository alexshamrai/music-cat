package io.github.alexshamrai;

import io.github.alexshamrai.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Central auth fix for MockMvc tests now that every path requires HTTP Basic:
 * imports the production {@link SecurityConfig} (so slices get the real chain with CSRF
 * disabled, not Boot's locked-down default) and runs each test as an authenticated user.
 * Production security stays untouched — no permitAll anywhere.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Import(SecurityConfig.class)
@WithMockUser
public @interface WithAuthenticatedUser {}
