package com.mirrorsoul.mirrorsoul_api.common.jwt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.common.security.*;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.service.UserActivityService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
class JwtAccountRestrictionTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void blockedAndPreviouslyIssuedTokensCannotAuthenticate() throws Exception {
        User user=User.builder().id(1L).email("a@example.com").passwordHash("hash").status(UserStatus.ACTIVE).build();
        var provider=new JwtTokenProvider(new JwtProperties("01234567890123456789012345678901",3600L,1209600L));
        var details=mock(CustomUserDetailsService.class);
        var activity=mock(UserActivityService.class);
        var filter=new JwtAuthenticationFilter(provider,details,activity);
        String old=provider.createAccessToken(user);
        user.suspend();
        when(details.loadUserById(1L)).thenAnswer(i -> new CustomUserDetails(user));
        authenticate(filter,old);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        user.unsuspend();
        authenticate(filter,old);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(activity);
        authenticate(filter,provider.createAccessToken(user));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(activity).touch(1L);
    }
    private void authenticate(JwtAuthenticationFilter filter,String token) throws Exception {
        var request=new MockHttpServletRequest("GET","/profile");
        request.addHeader("Authorization","Bearer "+token);
        filter.doFilter(request,new MockHttpServletResponse(),new MockFilterChain());
    }
}
