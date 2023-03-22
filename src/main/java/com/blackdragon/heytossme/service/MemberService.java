package com.blackdragon.heytossme.service;


import static com.blackdragon.heytossme.exception.errorcode.MemberErrorCode.INCORRECT_PASSWORD;
import static com.blackdragon.heytossme.exception.errorcode.MemberErrorCode.MATCH_PREVIOUS_PASSWORD;
import static com.blackdragon.heytossme.exception.errorcode.MemberErrorCode.MEMBER_NOT_FOUND;

import com.blackdragon.heytossme.component.TokenProvider;
import com.blackdragon.heytossme.dto.MemberDto;
import com.blackdragon.heytossme.dto.MemberDto.DeleteRequest;
import com.blackdragon.heytossme.dto.MemberDto.ModifyRequest;
import com.blackdragon.heytossme.dto.MemberDto.Response;
import com.blackdragon.heytossme.dto.MemberDto.ResponseToken;
import com.blackdragon.heytossme.dto.MemberDto.SignInRequest;
import com.blackdragon.heytossme.dto.MemberDto.SignOutResponse;
import com.blackdragon.heytossme.dto.MemberDto.SignUpRequest;
import com.blackdragon.heytossme.exception.MemberException;
import com.blackdragon.heytossme.exception.errorcode.MemberErrorCode;
import com.blackdragon.heytossme.persist.MemberRepository;
import com.blackdragon.heytossme.persist.entity.Member;
import com.blackdragon.heytossme.type.MemberSocialType;
import com.blackdragon.heytossme.type.MemberStatus;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ModelMapper modelMapper;
    private final TokenProvider tokenProvider;


    public MemberDto.Response signUp(SignUpRequest request) {

        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        Member member = memberRepository.save(
                Member.builder()
                        .email(request.getEmail())
                        .name(request.getName())
                        .password(passwordEncoder.encode(request.getPassword()))
                        .imageUrl(request.getImageUrl())
                        .socialLoginType(MemberSocialType.EMAIL.name())
                        .status(MemberStatus.NORMAL.name())
                        .account(request.getAccount())
                        .bankName(request.getBankName())
                        .build()
        );
        return new Response(member);
    }

    public Member signIn(SignInRequest request) {

        Optional<Member> byEmail = memberRepository.findByEmail(request.getEmail());

        Member member = byEmail.orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new MemberException(MemberErrorCode.INCORRECT_PASSWORD);
        }

        return member;
    }

    public Response getInfo(long userId) {

        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));

        return modelMapper.map(member, Response.class);
    }

    public Response modifyInfo(long userId, ModifyRequest request) {

        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));

        log.info(passwordEncoder.encode(request.getCurPassword()));
        // 사용자 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurPassword(), member.getPassword())) {
            throw new MemberException(INCORRECT_PASSWORD);
        }
        // 변경 후 비밀번호가 변경 전 비밀번호와 같을때
        if (passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new MemberException(MATCH_PREVIOUS_PASSWORD);
        }

        Member updatedMember = Member.builder()
                .id(member.getId())
                .email(request.getEmail() != null ? request.getEmail() : member.getEmail())
                .name(request.getName() != null ? request.getName() : member.getName())
                .password(request.getPassword() != null ?
                        passwordEncoder.encode(request.getPassword()) : member.getPassword())
                .imageUrl(request.getImageUrl() != null ? request.getImageUrl()
                        : member.getImageUrl())
                .socialLoginType(request.getSocialType() != null ? request.getSocialType()
                        : member.getSocialLoginType())
                .account(request.getAccount() != null ? request.getAccount() : member.getAccount())
                .bankName(request.getBankName() != null ? request.getBankName()
                        : member.getBankName())
                .status(member.getStatus())
                .build();

        memberRepository.save(updatedMember);

        return modelMapper.map(updatedMember, Response.class);
    }

    public void deleteUser(long userId, DeleteRequest request) {

        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));
        // 사용자 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurPassword(), member.getPassword())) {
            throw new MemberException(INCORRECT_PASSWORD);
        }

        /*
          회원탈퇴 시 실제 DB 에서 Delete 를 하지 않고 유저 status 값을 탈퇴로 바꾼다
          탈퇴 처리한 유저가 다시 가입할 경우를 대비하여 이메일을 공백 값으로 둔다.
         */

        Member updateStatus = Member.builder()
                .id(member.getId())
                .email(" ")
                .name(member.getName())
                .password(" ")
                .imageUrl(member.getImageUrl())
                .socialLoginType(member.getSocialLoginType())
                .status(MemberStatus.QUIT.name())
                .build();

        memberRepository.save(updateStatus);
    }

    public ResponseToken generateToken(Long id, String email) {

        String accessToken = tokenProvider.generateToken(id, email, true);
        String refreshToken = tokenProvider.generateToken(id, email, false);

        return ResponseToken.builder()
                .refreshToken(refreshToken)
                .accessToken(accessToken)
                .build();
    }

    public Cookie generateCookie(String refreshToken) {

        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setPath("/");
        cookie.setMaxAge(86400000);
        cookie.setSecure(true);
        cookie.setHttpOnly(true);

        return cookie;
    }

    public Cookie deleteCookie() {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");

        return cookie;
    }

    public SignOutResponse signOut(Long userId) {

        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        return MemberDto.SignOutResponse.from(member);
    }
}
