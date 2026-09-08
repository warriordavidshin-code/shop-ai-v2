package com.petitcamel.shop.member.service;

import com.petitcamel.shop.admin.dto.AdminMemberResponse;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.common.util.AgeCalculator;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberStatus;
import com.petitcamel.shop.member.dto.MemberResponse;
import com.petitcamel.shop.member.dto.PasswordChangeRequest;
import com.petitcamel.shop.member.dto.UpdateMemberRequest;
import com.petitcamel.shop.member.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgeCalculator ageCalculator;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public MemberService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            AgeCalculator ageCalculator,
            AuditLogService auditLogService,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.ageCalculator = ageCalculator;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        return toResponse(requireMember(memberId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminMemberResponse> listAdminMembers(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Page<Member> members = memberRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize));
        List<AdminMemberResponse> content = members.getContent().stream()
                .map(this::toAdminResponse)
                .toList();
        return PageResponse.of(content, members.getNumber(), members.getSize(), members.getTotalElements());
    }

    @Transactional
    public AdminMemberResponse updateAdminStatus(Long memberId, MemberStatus newStatus, Long actorMemberId) {
        Member member = requireMember(memberId);
        MemberStatus previous = member.getStatus();
        if (previous == newStatus) {
            return toAdminResponse(member);
        }

        member.setStatus(newStatus);
        member.setUpdatedAt(clock.instant());
        Member saved = memberRepository.save(member);

        auditLogService.record(
                actorMemberId,
                "MEMBER_STATUS_UPDATE",
                "MEMBER",
                String.valueOf(memberId),
                "from=" + previous + ", to=" + newStatus);

        return toAdminResponse(saved);
    }

    @Transactional
    public MemberResponse updateMe(Long memberId, UpdateMemberRequest request) {
        Member member = requireMember(memberId);
        if (request.name() != null) {
            member.setName(request.name().trim());
        }
        if (request.birthDate() != null) {
            validateAgePolicy(request.birthDate());
            member.setBirthDate(request.birthDate());
        }
        if (request.gender() != null) {
            member.setGender(request.gender());
        }
        if (request.phone() != null) {
            member.setPhone(request.phone().trim());
        }
        if (request.postcode() != null) {
            member.setPostcode(request.postcode().trim());
        }
        if (request.address1() != null) {
            member.setAddress1(request.address1().trim());
        }
        if (request.address2() != null) {
            member.setAddress2(request.address2().trim());
        }
        member.setUpdatedAt(clock.instant());
        return toResponse(memberRepository.save(member));
    }

    @Transactional
    public void changePassword(Long memberId, PasswordChangeRequest request) {
        Member member = requireMember(memberId);
        if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다.");
        }
        member.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        member.setUpdatedAt(clock.instant());
        memberRepository.save(member);
    }

    public MemberResponse toResponse(Member member) {
        return new MemberResponse(
                member.getMemberId(),
                member.getEmail(),
                member.getName(),
                member.getBirthDate(),
                ageCalculator.ageInYears(member.getBirthDate()),
                member.getGender(),
                member.getPhone(),
                member.getPostcode(),
                member.getAddress1(),
                member.getAddress2(),
                member.getRole());
    }

    public AdminMemberResponse toAdminResponse(Member member) {
        return new AdminMemberResponse(
                member.getMemberId(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getLastLoginAt());
    }

    public void validateAgePolicy(LocalDate birthDate) {
        int age = Period.between(birthDate, LocalDate.now(clock)).getYears();
        if (age < 14) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "만 14세 미만은 법정대리인 동의 없이는 가입할 수 없습니다. 보호자 동의 절차가 필요합니다.");
        }
    }

    public Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }
}
