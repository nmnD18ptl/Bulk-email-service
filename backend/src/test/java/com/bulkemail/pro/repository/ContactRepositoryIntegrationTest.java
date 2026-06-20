package com.bulkemail.pro.repository;

import com.bulkemail.pro.config.TestContainersConfig;
import com.bulkemail.pro.model.entity.Contact;
import com.bulkemail.pro.model.entity.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use real PostgreSQL
@DisplayName("ContactRepository — PostgreSQL integration")
class ContactRepositoryIntegrationTest {

    @Autowired ContactRepository contactRepository;
    @Autowired OrganizationRepository organizationRepository;

    private Long orgId;

    @BeforeEach
    void setUp() {
        contactRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = Organization.createWithPlan("Test Org", "test-org", Organization.PlanType.STARTER);
        orgId = organizationRepository.save(org).getId();
    }

    @Test
    @DisplayName("saves and retrieves a contact by org + email (case-insensitive)")
    void savesAndFindsContact() {
        Contact c = contact("Alice", "Smith", "ALICE@EXAMPLE.COM");
        contactRepository.save(c);

        Optional<Contact> found = contactRepository
                .findByOrganizationIdAndEmailIgnoreCase(orgId, "alice@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getFirstName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("enforces unique email per organization")
    void rejectsEmailDuplicateWithinOrg() {
        contactRepository.save(contact("Alice", "Smith", "alice@example.com"));

        Contact duplicate = contact("Another", "Alice", "alice@example.com");

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> contactRepository.saveAndFlush(duplicate)
        );
    }

    @Test
    @DisplayName("allows same email in different organizations")
    void allowsSameEmailAcrossOrgs() {
        Organization org2 = Organization.createWithPlan("Org 2", "org-2", Organization.PlanType.FREE);
        Long org2Id = organizationRepository.save(org2).getId();

        contactRepository.save(contact("Alice", "Org1", "shared@example.com"));

        Contact c2 = contact("Alice", "Org2", "shared@example.com");
        c2.setOrganizationId(org2Id);
        contactRepository.save(c2);

        assertThat(contactRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("counts contacts by status within org")
    void countsActiveContacts() {
        contactRepository.saveAll(List.of(
                contact("A", "1", "a1@x.com"),
                contact("A", "2", "a2@x.com"),
                withStatus(contact("B", "3", "b3@x.com"), Contact.ContactStatus.UNSUBSCRIBED)
        ));

        long active = contactRepository.countByOrganizationIdAndStatus(
                orgId, Contact.ContactStatus.ACTIVE);

        assertThat(active).isEqualTo(2);
    }

    @Test
    @DisplayName("pages contacts correctly")
    void paginatesContacts() {
        for (int i = 0; i < 25; i++) {
            contactRepository.save(contact("User", String.valueOf(i),
                    "user" + i + "@example.com"));
        }

        Page<Contact> page = contactRepository.findByOrganizationId(
                orgId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("filters contacts by status")
    void filtersContactsByStatus() {
        contactRepository.saveAll(List.of(
                contact("Active", "1", "active1@x.com"),
                contact("Active", "2", "active2@x.com"),
                withStatus(contact("Bounced", "3", "bounced@x.com"), Contact.ContactStatus.BOUNCED)
        ));

        Page<Contact> active = contactRepository.findByOrganizationIdAndStatus(
                orgId, Contact.ContactStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(active.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("cross-tenant isolation: org A cannot see org B contacts")
    void tenantIsolation() {
        Organization orgB = Organization.createWithPlan("Org B", "org-b", Organization.PlanType.FREE);
        Long orgBId = organizationRepository.save(orgB).getId();

        contactRepository.save(contact("OrgA", "User", "orgauser@example.com"));

        Contact orgBContact = contact("OrgB", "User", "orgbuser@example.com");
        orgBContact.setOrganizationId(orgBId);
        contactRepository.save(orgBContact);

        Page<Contact> orgAContacts = contactRepository.findByOrganizationId(
                orgId, PageRequest.of(0, 20));

        assertThat(orgAContacts.getContent())
                .allMatch(c -> c.getOrganizationId().equals(orgId))
                .hasSize(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Contact contact(String firstName, String lastName, String email) {
        Contact c = new Contact();
        c.setOrganizationId(orgId);
        c.setFirstName(firstName);
        c.setLastName(lastName);
        c.setEmail(email);
        c.setStatus(Contact.ContactStatus.ACTIVE);
        return c;
    }

    private Contact withStatus(Contact c, Contact.ContactStatus status) {
        c.setStatus(status);
        return c;
    }
}
