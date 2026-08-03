package com.skysport.datn.config;

import com.skysport.datn.entity.Account;
import com.skysport.datn.enums.RoleName;
import com.skysport.datn.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + username));

        String roleName = account.getRole() != null ? account.getRole().getName() : "";

        String springRole;
        if (RoleName.ADMIN.matches(roleName))         springRole = "ROLE_ADMIN";
        else if (RoleName.STAFF.matches(roleName))    springRole = "ROLE_STAFF";
        else                                           springRole = "ROLE_CUSTOMER";

        return new org.springframework.security.core.userdetails.User(
                account.getUsername(),
                account.getPassword(),
                account.getStatus() != null && account.getStatus() == 1,
                true,
                true,
                account.getIsNonLocked() == null || account.getIsNonLocked(),
                List.of(new SimpleGrantedAuthority(springRole))
        );
    }
}