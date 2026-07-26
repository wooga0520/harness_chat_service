package com.example.chatservice.user;

import com.example.chatservice.repository.UserRepository;
import com.example.chatservice.user.dto.UserSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_RESULTS = 20;

    private final UserRepository userRepository;

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> search(@RequestParam String q, Principal principal) {
        Long selfId = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"))
                .getId();

        List<UserSearchResponse> results = userRepository
                .searchByUsernameOrNickname(q.trim(), selfId, PageRequest.of(0, MAX_RESULTS))
                .stream()
                .map(UserSearchResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }
}
