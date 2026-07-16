package com.chamchamcham.api.security

import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.member.MemberRepository
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class JwtAuthenticationFilterTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Mock private lateinit var tokenProvider: TokenProvider
    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var memberRepositoryProvider: ObjectProvider<MemberRepository>
    @Mock private lateinit var chain: FilterChain

    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var authentication: Authentication
    private lateinit var filter: JwtAuthenticationFilter

    @BeforeEach
    fun setUp() {
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        authentication = UsernamePasswordAuthenticationToken(memberId.toString(), null, emptyList())
        filter = JwtAuthenticationFilter(tokenProvider, memberRepositoryProvider)
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `valid access token authenticates existing member`() {
        `when`(memberRepositoryProvider.ifAvailable).thenReturn(memberRepository)
        request.addHeader("Authorization", "Bearer access-token")
        `when`(tokenProvider.validateToken("access-token")).thenReturn(true)
        `when`(tokenProvider.isAccessToken("access-token")).thenReturn(true)
        `when`(tokenProvider.getMemberId("access-token")).thenReturn(memberId)
        `when`(memberRepository.existsById(memberId)).thenReturn(true)
        `when`(tokenProvider.getAuthentication("access-token")).thenReturn(authentication)

        filter.doFilter(request, response, chain)

        assertSame(authentication, SecurityContextHolder.getContext().authentication)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `valid token for deleted member stays unauthenticated`() {
        `when`(memberRepositoryProvider.ifAvailable).thenReturn(memberRepository)
        request.addHeader("Authorization", "Bearer access-token")
        `when`(tokenProvider.validateToken("access-token")).thenReturn(true)
        `when`(tokenProvider.isAccessToken("access-token")).thenReturn(true)
        `when`(tokenProvider.getMemberId("access-token")).thenReturn(memberId)
        `when`(memberRepository.existsById(memberId)).thenReturn(false)

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(tokenProvider, never()).getAuthentication(anyString())
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `valid token stays unauthenticated when member repository is unavailable`() {
        `when`(memberRepositoryProvider.ifAvailable).thenReturn(null)
        request.addHeader("Authorization", "Bearer access-token")
        `when`(tokenProvider.validateToken("access-token")).thenReturn(true)
        `when`(tokenProvider.isAccessToken("access-token")).thenReturn(true)
        `when`(tokenProvider.getMemberId("access-token")).thenReturn(memberId)

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(tokenProvider, never()).getAuthentication(anyString())
        verify(chain).doFilter(request, response)
    }
}
