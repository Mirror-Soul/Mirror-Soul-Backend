package com.mirrorsoul.mirrorsoul_api.service;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class WithdrawnAccountCleanupService {
    private final UserRepository userRepository;
    private final MemberDeletionService deletion;
    @Transactional
    public int anonymizeExpiredAccounts(LocalDateTime now) {
        List<User> users=userRepository.findAllByStatusAndWithdrawnAtLessThanEqual(UserStatus.INACTIVE,now.minusDays(30));
        for (User user:users) {
            user.confirmWithdrawal();
            deletion.enqueue(user,now);
        }
        return users.size();
    }
}
