package com.godsmove.config

import com.godsmove.api.security.CustomAccessDeniedHandler
import com.godsmove.api.security.CustomAuthenticationEntryPoint
import com.godsmove.api.security.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val accessDeniedHandler: CustomAccessDeniedHandler,
    private val authenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val mdcLoggingFilter: MDCLoggingFilter,
    private val environment: Environment,
    @Value("\${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    allowedCorsOrigins: String
) {
    private val corsAllowedOrigins = parseCorsOrigins(allowedCorsOrigins)

    init {
        require(corsAllowedOrigins.isNotEmpty()) {
            "app.cors.allowed-origins must contain at least one origin"
        }
        require(corsAllowedOrigins.none { it == "*" || it.contains("*") }) {
            "Credentialed CORS must use explicit origins, not wildcards"
        }
    }

    companion object {
        private val PUBLIC_ENDPOINTS = listOf(
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1/auth/email/send-code",
            "/api/v1/auth/email/verify-code",
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/kakao/login",
            "/api/v1/auth/reissue",
            "/error",                // 스프링 내부 오류 페이지
        )

        private val LOCAL_PUBLIC_ENDPOINTS = listOf(
            "/api/v1/dev/rag/seed",
        )
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {

        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }

            .cors {
                it.configurationSource {
                    CorsConfiguration().apply {
                        allowedOrigins = corsAllowedOrigins
                        allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        allowedHeaders = listOf("*")
                        allowCredentials = true
                    }
                }
            }

            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }

            .authorizeHttpRequests {
                it
                    .requestMatchers(*publicEndpoints().toTypedArray())
                    .permitAll()
                    .anyRequest().authenticated()
            }

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )
            .addFilterAfter(
                mdcLoggingFilter,
                JwtAuthenticationFilter::class.java
            )

        return http.build()
    }

    @Bean
    fun loggingFilterRegistration(mdcLoggingFilter: MDCLoggingFilter): FilterRegistrationBean<MDCLoggingFilter> {
        return FilterRegistrationBean<MDCLoggingFilter>().apply {
            filter = mdcLoggingFilter
            isEnabled = false
            addUrlPatterns("/*")
        }
    }

    private fun parseCorsOrigins(value: String): List<String> {
        return value.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    private fun publicEndpoints(): List<String> {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            return PUBLIC_ENDPOINTS
        }
        return PUBLIC_ENDPOINTS + LOCAL_PUBLIC_ENDPOINTS
    }

}
