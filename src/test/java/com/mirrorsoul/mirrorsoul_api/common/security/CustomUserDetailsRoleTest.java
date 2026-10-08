package com.mirrorsoul.mirrorsoul_api.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserRole;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomUserDetailsRoleTest {

    @Test
    void onlyActiveAdminsReceiveAdminAuthority() {
        assertThat(details(UserRole.USER, UserStatus.ACTIVE).getAuthorities()).isEmpty();
        assertThat(details(UserRole.ADMIN, UserStatus.ACTIVE).getAuthorities())
                .extracting(authority -> authority.getAuthority()).containsExactly("ROLE_ADMIN");
        assertThat(details(UserRole.ADMIN, UserStatus.INACTIVE).getAuthorities()).isEmpty();
    }

    private CustomUserDetails details(UserRole role, UserStatus status) {
        return new CustomUserDetails(User.builder()
                .id(1L).uuid(UUID.randomUUID()).email("test@example.com")
                .passwordHash("hash").role(role).status(status).build());
    }
}
