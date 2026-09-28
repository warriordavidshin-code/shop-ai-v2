package com.petitcamel.shop.member.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberAddress;
import com.petitcamel.shop.member.dto.MemberAddressRequest;
import com.petitcamel.shop.member.dto.MemberAddressResponse;
import com.petitcamel.shop.member.repository.MemberAddressRepository;
import com.petitcamel.shop.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAddressServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    MemberAddressRepository addressRepository;
    @Mock
    MemberRepository memberRepository;

    MemberAddressService service;
    Member member;

    @BeforeEach
    void setUp() {
        service = new MemberAddressService(addressRepository, memberRepository, CLOCK);
        member = new Member();
        member.setMemberId(1L);
        member.setName("홍길동");
    }

    @Test
    void firstAddressBecomesDefaultAndSyncsProfile() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(addressRepository.countByMemberId(1L)).thenReturn(0L);
        when(addressRepository.save(any(MemberAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberAddressResponse response = service.create(1L, request("집", false));

        assertThat(response.defaultAddress()).isTrue();
        verify(addressRepository).clearDefault(1L);
        assertThat(member.getPostcode()).isEqualTo("06236");
        assertThat(member.getAddress1()).isEqualTo("서울 강남구 테헤란로 1");
        verify(memberRepository).save(member);
    }

    @Test
    void nonDefaultAddressDoesNotTouchProfile() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(addressRepository.countByMemberId(1L)).thenReturn(2L);
        when(addressRepository.save(any(MemberAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberAddressResponse response = service.create(1L, request("회사", false));

        assertThat(response.defaultAddress()).isFalse();
        verify(addressRepository, never()).clearDefault(1L);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void rejectsMoreThanMaxAddresses() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(addressRepository.countByMemberId(1L)).thenReturn((long) MemberAddressService.MAX_ADDRESSES);

        assertThatThrownBy(() -> service.create(1L, request("집", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void setDefaultClearsPreviousDefault() {
        MemberAddress address = address(5L, false);
        when(addressRepository.findByAddressIdAndMemberId(5L, 1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(MemberAddress.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberAddressResponse response = service.setDefault(1L, 5L);

        assertThat(response.defaultAddress()).isTrue();
        verify(addressRepository).clearDefault(1L);
        verify(memberRepository).save(member);
    }

    @Test
    void deletingDefaultPromotesMostRecentRemainingAddress() {
        MemberAddress removed = address(5L, true);
        MemberAddress next = address(6L, false);
        when(addressRepository.findByAddressIdAndMemberId(5L, 1L)).thenReturn(Optional.of(removed));
        when(addressRepository.findByMemberIdOrderByDefaultAddressDescUpdatedAtDesc(1L)).thenReturn(List.of(next));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        service.delete(1L, 5L);

        verify(addressRepository).delete(removed);
        assertThat(next.isDefaultAddress()).isTrue();
        verify(addressRepository).save(next);
        verify(memberRepository).save(member);
    }

    @Test
    void addressOfAnotherMemberIsNotFound() {
        when(addressRepository.findByAddressIdAndMemberId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void listSeedsProfileAddressWhenBookIsEmpty() {
        member.setPhone("01012345678");
        member.setPostcode("30100");
        member.setAddress1("세종");
        when(addressRepository.findByMemberIdOrderByDefaultAddressDescUpdatedAtDesc(1L)).thenReturn(List.of());
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(addressRepository.save(any(MemberAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        List<MemberAddressResponse> result = service.list(1L);

        assertThat(result).hasSize(1);
        ArgumentCaptor<MemberAddress> captor = ArgumentCaptor.forClass(MemberAddress.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefaultAddress()).isTrue();
        assertThat(captor.getValue().getLabel()).isEqualTo(MemberAddressService.PROFILE_ADDRESS_LABEL);
    }

    @Test
    void listDoesNotSeedWithoutCompleteProfileAddress() {
        when(addressRepository.findByMemberIdOrderByDefaultAddressDescUpdatedAtDesc(1L)).thenReturn(List.of());
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThat(service.list(1L)).isEmpty();
        verify(addressRepository, never()).save(any());
    }

    private static MemberAddressRequest request(String label, boolean defaultAddress) {
        return new MemberAddressRequest(
                label, "홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "3층", defaultAddress);
    }

    private static MemberAddress address(Long id, boolean isDefault) {
        MemberAddress address = new MemberAddress();
        address.setAddressId(id);
        address.setMemberId(1L);
        address.setLabel("주소" + id);
        address.setReceiverName("홍길동");
        address.setReceiverPhone("01012345678");
        address.setPostcode("06236");
        address.setAddress1("서울 강남구");
        address.setDefaultAddress(isDefault);
        return address;
    }
}
