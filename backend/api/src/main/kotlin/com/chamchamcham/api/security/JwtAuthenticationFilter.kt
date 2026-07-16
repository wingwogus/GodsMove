package com.chamchamcham.api.security

import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.member.MemberRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val tokenProvider: TokenProvider,
    private val memberRepositoryProvider: ObjectProvider<MemberRepository>
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token != null && tokenProvider.validateToken(token) && tokenProvider.isAccessToken(token)) {
            val memberId = tokenProvider.getMemberId(token)
            val memberRepository = memberRepositoryProvider.ifAvailable
            if (memberRepository != null && memberRepository.existsById(memberId)) {
                SecurityContextHolder.getContext().authentication = tokenProvider.getAuthentication(token)
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearer = request.getHeader("Authorization")
        return if (bearer != null && bearer.startsWith("Bearer ")) {
            bearer.substring(7)
        } else null
    }
}
