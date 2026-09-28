package com.petitcamel.shop.member.repository;

import com.petitcamel.shop.member.domain.MemberAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

    List<MemberAddress> findByMemberIdOrderByDefaultAddressDescUpdatedAtDesc(Long memberId);

    Optional<MemberAddress> findByAddressIdAndMemberId(Long addressId, Long memberId);

    long countByMemberId(Long memberId);

    /**
     * Must run (and flush) before another row is marked default, otherwise the partial unique
     * index {@code uq_member_address_default} rejects the second default.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MemberAddress a SET a.defaultAddress = false WHERE a.memberId = :memberId AND a.defaultAddress = true")
    int clearDefault(@Param("memberId") Long memberId);
}
