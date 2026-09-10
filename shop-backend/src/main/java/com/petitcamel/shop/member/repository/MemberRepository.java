package com.petitcamel.shop.member.repository;

import com.petitcamel.shop.member.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Member> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
