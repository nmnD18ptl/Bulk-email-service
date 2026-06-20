package com.bulkemail.pro.repository;

import com.bulkemail.pro.model.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    List<Template> findByIsActive(boolean active);
    List<Template> findByCategory(String category);

    @Query("SELECT t FROM Template t WHERE t.isBuiltIn = :builtIn")
    List<Template> findByIsBuiltIn(boolean builtIn);

    // Org-scoped: return org's own templates + built-in shared templates
    @Query("SELECT t FROM Template t WHERE (t.organizationId = :orgId OR t.isBuiltIn = true) AND t.isActive = true")
    List<Template> findByOrganizationIdOrBuiltIn(@Param("orgId") Long orgId);

    @Query("SELECT t FROM Template t WHERE (t.organizationId = :orgId OR t.isBuiltIn = true) AND t.category = :category AND t.isActive = true")
    List<Template> findByOrganizationIdOrBuiltInAndCategory(@Param("orgId") Long orgId, @Param("category") String category);
}
