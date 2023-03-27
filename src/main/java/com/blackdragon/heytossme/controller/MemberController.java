package com.blackdragon.heytossme.controller;

import com.blackdragon.heytossme.dto.MemberDto.DeleteRequest;
import com.blackdragon.heytossme.dto.MemberDto.ModifyRequest;
import com.blackdragon.heytossme.dto.MemberDto.Response;
import com.blackdragon.heytossme.dto.MemberDto.ResponseToken;
import com.blackdragon.heytossme.dto.MemberDto.SignInRequest;
import com.blackdragon.heytossme.dto.MemberDto.SignInResponse;
import com.blackdragon.heytossme.dto.MemberDto.SignOutResponse;
import com.blackdragon.heytossme.dto.MemberDto.SignUpRequest;
import com.blackdragon.heytossme.dto.ResponseForm;
import com.blackdragon.heytossme.persist.entity.Member;
import com.blackdragon.heytossme.service.MemberService;
import com.blackdragon.heytossme.type.resposne.MemberResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
@Slf4j
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<ResponseForm> signUp(@Valid @RequestBody SignUpRequest request) {
        var data = memberService.signUp(request);
        return ResponseEntity.ok(new ResponseForm(MemberResponse.SIGN_UP.getMessage(), data));
    }

    @PostMapping("/signIn")
    public ResponseEntity<?> signIn(@RequestBody SignInRequest request,
//                                    @RequestBody String messageToken,
                                    HttpServletResponse response) {

        Member member = memberService.signIn(request);
        ResponseToken tokens = memberService.generateToken(member.getId(), member.getEmail());
        ResponseCookie cookie = memberService.generateCookie(tokens.getRefreshToken());

        SignInResponse data = SignInResponse.builder()
                .id(member.getId())
                .account(member.getAccount())
                .build();

        response.addHeader("Set-cookie", cookie.toString());


        return ResponseEntity.ok()
                .body(new ResponseForm(
                        MemberResponse.SIGN_UP.getMessage(), data, tokens.getAccessToken()));
    }

    /**
     * Interceptor로부터 넘어오는 로그아웃 API
     */
    @GetMapping("/v2/members/logout/{refreshToken}/{userId}")
    public ResponseEntity<ResponseForm> logout( @PathVariable("accessToken") String token,
                                                @PathVariable("userId") Object _userId) {

        Long userId = Long.valueOf(String.valueOf(_userId));

        HttpServletResponse response
                = ((ServletRequestAttributes) RequestContextHolder
                                            .currentRequestAttributes()).getResponse();

        Cookie cookie = memberService.deleteCookie();
        response.addCookie(cookie);

        SignOutResponse data = memberService.signOut(userId);

        return ResponseEntity.ok(
                new ResponseForm(MemberResponse.SIGN_OUT.getMessage(), data, token));
    }

    @GetMapping
    public ResponseEntity<ResponseForm> getInfo(HttpServletRequest httpServletRequest) {
        long id = (long) httpServletRequest.getAttribute("id");
        Response response = memberService.getInfo(id);

        return ResponseEntity.ok(
                new ResponseForm(MemberResponse.FIND_INFO.getMessage(), response));
    }

    @PatchMapping
    public ResponseEntity<ResponseForm> modifyInfo(HttpServletRequest httpServletRequest,
            @Valid @RequestBody ModifyRequest request) {

        long id = (long) httpServletRequest.getAttribute("id");
        Response response = memberService.modifyInfo(id, request);

        return ResponseEntity.ok(
                new ResponseForm(MemberResponse.CHANGE_INFO.getMessage(), response));
    }

    @DeleteMapping
    public ResponseEntity<ResponseForm> delete(HttpServletRequest httpServletRequest,
            @Valid @RequestBody DeleteRequest request) {

        long id = (long) httpServletRequest.getAttribute("id");
        memberService.deleteUser(id, request);

        return ResponseEntity.ok(
                new ResponseForm(MemberResponse.DELETE_USER.getMessage(), null));
    }

    //Access토큰 재발급
    @GetMapping("/v2/members/token/re-create/{userId}")
    public ResponseEntity<ResponseForm> recreateToken(HttpServletRequest request,
            HttpServletResponse response,@PathVariable Long userId) {

        String generatedToken = memberService.reCreateAccessToken(request, response, userId);

        return ResponseEntity.ok(
                new ResponseForm(MemberResponse.RE_CREATED_ACCESS_TOKEN.getMessage(),
                        null, generatedToken));
    }
}
