package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class JoinService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public JoinResDTO.basicProfileResDTO basicProfile(JoinReqDTO.basicProfileReqDTO req){

        String encodedPassword = passwordEncoder.encode(req.getPassword());

        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(encodedPassword)
                .gender(req.getGender())
                .birthDate(req.getBirthDate())
                .status(com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus.NOT_COMPLETED)
                .build();

        userRepository.save(user);

        return JoinResDTO.basicProfileResDTO.builder()
                .userId(user.getId())
                .build();
    }
}
