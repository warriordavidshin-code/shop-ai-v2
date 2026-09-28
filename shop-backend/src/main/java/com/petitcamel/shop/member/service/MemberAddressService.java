package com.petitcamel.shop.member.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberAddress;
import com.petitcamel.shop.member.dto.MemberAddressRequest;
import com.petitcamel.shop.member.dto.MemberAddressResponse;
import com.petitcamel.shop.member.repository.MemberAddressRepository;
import com.petitcamel.shop.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class MemberAddressService {

    static final int MAX_ADDRESSES = 10;
    static final String PROFILE_ADDRESS_LABEL = "기본 배송지";

    private final MemberAddressRepository addressRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public MemberAddressService(
            MemberAddressRepository addressRepository,
            MemberRepository memberRepository,
            Clock clock) {
        this.addressRepository = addressRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /**
     * Members who signed up before the address book existed get their profile address
     * copied in as the default entry on first access.
     */
    @Transactional
    public List<MemberAddressResponse> list(Long memberId) {
        List<MemberAddress> addresses = addressRepository.findByMemberIdOrderByDefaultAddressDescUpdatedAtDesc(memberId);
        if (addresses.isEmpty()) {
            MemberAddress seeded = seedFromProfile(requireMember(memberId));
            if (seeded != null) {
                addresses = List.of(seeded);
            }
        }
        return addresses.stream().map(MemberAddressService::toResponse).toList();
    }

    @Transactional
    public MemberAddressResponse create(Long memberId, MemberAddressRequest request) {
        Member member = requireMember(memberId);
        long count = addressRepository.countByMemberId(memberId);
        if (count >= MAX_ADDRESSES) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "배송지는 최대 " + MAX_ADDRESSES + "개까지 등록할 수 있습니다.");
        }
        boolean makeDefault = request.defaultAddress() || count == 0;
        if (makeDefault) {
            addressRepository.clearDefault(memberId);
        }

        Instant now = clock.instant();
        MemberAddress address = new MemberAddress();
        address.setMemberId(memberId);
        apply(address, request);
        address.setDefaultAddress(makeDefault);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        MemberAddress saved = addressRepository.save(address);

        if (makeDefault) {
            syncProfileAddress(member, saved, now);
        }
        return toResponse(saved);
    }

    @Transactional
    public MemberAddressResponse update(Long memberId, Long addressId, MemberAddressRequest request) {
        MemberAddress address = requireAddress(memberId, addressId);
        boolean makeDefault = request.defaultAddress() && !address.isDefaultAddress();
        if (makeDefault) {
            addressRepository.clearDefault(memberId);
            address = requireAddress(memberId, addressId);
        }

        Instant now = clock.instant();
        apply(address, request);
        if (makeDefault) {
            address.setDefaultAddress(true);
        }
        address.setUpdatedAt(now);
        MemberAddress saved = addressRepository.save(address);

        if (saved.isDefaultAddress()) {
            syncProfileAddress(requireMember(memberId), saved, now);
        }
        return toResponse(saved);
    }

    @Transactional
    public MemberAddressResponse setDefault(Long memberId, Long addressId) {
        MemberAddress address = requireAddress(memberId, addressId);
        if (address.isDefaultAddress()) {
            return toResponse(address);
        }
        addressRepository.clearDefault(memberId);
        address = requireAddress(memberId, addressId);

        Instant now = clock.instant();
        address.setDefaultAddress(true);
        address.setUpdatedAt(now);
        MemberAddress saved = addressRepository.save(address);
        syncProfileAddress(requireMember(memberId), saved, now);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long memberId, Long addressId) {
        MemberAddress address = requireAddress(memberId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        addressRepository.flush();

        if (wasDefault) {
            List<MemberAddress> remaining =
                    addressRepository.findByMemberIdOrderByDefaultAddressDescUpdatedAtDesc(memberId);
            if (!remaining.isEmpty()) {
                MemberAddress next = remaining.get(0);
                Instant now = clock.instant();
                next.setDefaultAddress(true);
                next.setUpdatedAt(now);
                addressRepository.save(next);
                syncProfileAddress(requireMember(memberId), next, now);
            }
        }
    }

    private MemberAddress seedFromProfile(Member member) {
        if (!StringUtils.hasText(member.getPostcode())
                || !StringUtils.hasText(member.getAddress1())
                || !StringUtils.hasText(member.getPhone())) {
            return null;
        }
        Instant now = clock.instant();
        MemberAddress address = new MemberAddress();
        address.setMemberId(member.getMemberId());
        address.setLabel(PROFILE_ADDRESS_LABEL);
        address.setReceiverName(member.getName());
        address.setReceiverPhone(member.getPhone());
        address.setPostcode(member.getPostcode());
        address.setAddress1(member.getAddress1());
        address.setAddress2(member.getAddress2());
        address.setDefaultAddress(true);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        return addressRepository.save(address);
    }

    /** Keeps the member profile address (used by signup/social flows) equal to the default entry. */
    private void syncProfileAddress(Member member, MemberAddress address, Instant now) {
        member.setPostcode(address.getPostcode());
        member.setAddress1(address.getAddress1());
        member.setAddress2(address.getAddress2());
        member.setUpdatedAt(now);
        memberRepository.save(member);
    }

    private static void apply(MemberAddress address, MemberAddressRequest request) {
        address.setLabel(request.label().trim());
        address.setReceiverName(request.receiverName().trim());
        address.setReceiverPhone(request.receiverPhone().trim());
        address.setPostcode(request.postcode().trim());
        address.setAddress1(request.address1().trim());
        address.setAddress2(StringUtils.hasText(request.address2()) ? request.address2().trim() : null);
    }

    private MemberAddress requireAddress(Long memberId, Long addressId) {
        return addressRepository.findByAddressIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배송지를 찾을 수 없습니다."));
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    static MemberAddressResponse toResponse(MemberAddress address) {
        return new MemberAddressResponse(
                address.getAddressId(),
                address.getLabel(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getPostcode(),
                address.getAddress1(),
                address.getAddress2(),
                address.isDefaultAddress(),
                address.getUpdatedAt());
    }
}
