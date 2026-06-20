package com.bulkemail.pro.service;

import com.bulkemail.pro.model.entity.Organization;
import com.bulkemail.pro.model.entity.User;
import com.bulkemail.pro.repository.OrganizationRepository;
import com.bulkemail.pro.repository.UserRepository;
import com.bulkemail.pro.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtDenylistService jwtDenylistService;

    public Map<String, Object> register(String orgName, String email, String password,
                                        String firstName, String lastName) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        String slug = generateSlug(orgName);
        Organization org = Organization.createWithPlan(orgName, slug, Organization.PlanType.FREE);
        org = organizationRepository.save(org);

        User user = new User();
        user.setEmail(email.toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setOrganization(org);
        user.setRole(User.UserRole.OWNER);
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail(), org.getId(), user.getRole().name());
        return buildResponse(user, org, token);
    }

    public Map<String, Object> login(String email, String password) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!user.isActive()) {
            throw new IllegalArgumentException("Account is deactivated");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        Organization org = user.getOrganization();
        String token = jwtService.generateToken(user.getEmail(), org.getId(), user.getRole().name());
        return buildResponse(user, org, token);
    }

    public Map<String, Object> inviteUser(Long orgId, String email, String role, String firstName, String lastName) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        Organization org = organizationRepository.findById(orgId)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        User user = new User();
        user.setEmail(email.toLowerCase().trim());
        // Temporary password — user must reset via email (email reset not implemented yet)
        user.setPasswordHash(passwordEncoder.encode("ChangeMe@123"));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setOrganization(org);
        user.setRole(User.UserRole.valueOf(role.toUpperCase()));
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "User invited. Temporary password: ChangeMe@123");
        result.put("email", email);
        return result;
    }

    public List<User> getOrgUsers(Long orgId) {
        return userRepository.findByOrganizationId(orgId);
    }

    /**
     * Revokes the supplied JWT immediately by adding it to the Redis denylist.
     * The denylist entry auto-expires when the token's natural expiry is reached,
     * so no cleanup job is needed.
     */
    public void logout(String token) {
        long remainingMs = jwtService.extractRemainingMs(token);
        jwtDenylistService.deny(token, remainingMs);
    }

    public void removeUser(Long orgId, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.getOrganization().getId().equals(orgId)) {
            throw new SecurityException("Access denied");
        }
        if (user.getRole() == User.UserRole.OWNER) {
            throw new IllegalArgumentException("Cannot remove the organization owner");
        }
        userRepository.delete(user);
    }

    private Map<String, Object> buildResponse(User user, Organization org, String token) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("user", Map.of(
            "id", user.getId(),
            "email", user.getEmail(),
            "firstName", user.getFirstName() != null ? user.getFirstName() : "",
            "lastName", user.getLastName() != null ? user.getLastName() : "",
            "role", user.getRole().name(),
            "fullName", user.getFullName()
        ));
        resp.put("organization", Map.of(
            "id", org.getId(),
            "name", org.getName(),
            "slug", org.getSlug(),
            "plan", org.getPlan().name(),
            "monthlyEmailLimit", org.getMonthlyEmailLimit(),
            "emailsSentThisMonth", org.getEmailsSentThisMonth(),
            "maxContacts", org.getMaxContacts()
        ));
        return resp;
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");

        String slug = base;
        int counter = 1;
        while (organizationRepository.existsBySlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }
}
