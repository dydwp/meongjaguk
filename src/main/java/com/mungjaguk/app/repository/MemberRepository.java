package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByProviderAndProviderId(String provider, String providerId);
}