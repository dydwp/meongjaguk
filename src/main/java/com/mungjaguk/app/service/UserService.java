package com.mungjaguk.app.service;

import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.UserRepository;
import com.mungjaguk.app.security.OAuthAttributes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User loginOrJoin(OAuthAttributes attributes) {
        return userRepository
                .findByProviderAndProviderId(attributes.provider(), attributes.providerId())
                .orElseGet(() -> userRepository.save(User.create(
                        attributes.provider(),
                        attributes.providerId(),
                        attributes.nickname(),
                        attributes.email(),
                        attributes.profileImage())));
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));
    }
}