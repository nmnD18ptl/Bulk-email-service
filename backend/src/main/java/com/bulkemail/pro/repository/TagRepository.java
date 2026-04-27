package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);
    boolean existsByName(String name);

    Optional<Tag> findByNameAndOrganizationId(String name, Long organizationId);
    boolean existsByNameAndOrganizationId(String name, Long organizationId);

    @Query("SELECT t, COUNT(c) FROM Tag t LEFT JOIN t.contacts c WHERE t.organizationId = :orgId GROUP BY t")
    List<Object[]> findAllWithContactCountByOrganizationId(@Param("orgId") Long orgId);

    List<Tag> findByOrganizationId(Long organizationId);
}
