package com.ahmet.hierarchicalSecured;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import static com.ahmet.hierarchicalSecured.ReflectionUtils.getAnnotation;

@Component
@Aspect
@Slf4j
@RequiredArgsConstructor
public class HierarchicalSecuredAspect {
    private final BoRoleRepository boRoleRepository;

    @Around("@annotation(HierarchicalSecured)")
    public Object invokeHierarchicalSecured(ProceedingJoinPoint joinPoint) throws Throwable {

        HierarchicalSecured annotation =getAnnotation(joinPoint, HierarchicalSecured.class);

        List<BoRole> boRoles = findAllRoles();

        List<GrantedAuthority> userRoles = getUserRoles();

        checkBlockedRoles(annotation, userRoles);

        int minPriorityUserRoles = getMinPriorityUserRoles(boRoles, userRoles);

        checkUserRolePriorityLowerThanAnnotationRolePriority(
                minPriorityUserRoles, annotation.role(), boRoles);

        return joinPoint.proceed();
    }

    @Cacheable("findAllRoles")
    public List<BoRole> findAllRoles() {
        return boRoleRepository.findAll();
    }

    private List<GrantedAuthority> getUserRoles() {
        return new ArrayList<>(SecurityContextHolder.getContext().getAuthentication().getAuthorities());
    }

    private int getMinPriorityUserRoles(List<BoRole> boRoles, List<GrantedAuthority> userRoles) {
        AtomicInteger minPriority = new AtomicInteger(9999);
        for (GrantedAuthority role : userRoles) {
            boRoles.stream()
                    .filter(boRole -> boRole.getRoleType().name().equals(role.getAuthority()))
                    .findFirst()
                    .ifPresent(
                            boRole ->
                                    minPriority.set(
                                            boRole.getPriority() < minPriority.get()
                                                    ? boRole.getPriority()
                                                    : minPriority.get()));
        }
        return minPriority.get();
    }

    private void checkBlockedRoles(
            HierarchicalSecured hierarchicalSecured, List<GrantedAuthority> userAuthorities) {
        List<String> blockedRoles = Arrays.asList(hierarchicalSecured.blockedRoles());
        if (!blockedRoles.isEmpty()) {
            for (GrantedAuthority userAuthority : userAuthorities) {
                boolean block = blockedRoles.stream().anyMatch(userAuthority.getAuthority()::contains);
                if (block) {
                    throw new ForbiddenException();
                }
            }
        }
    }

    private void checkUserRolePriorityLowerThanAnnotationRolePriority(
            int minPriorityUserRoles, String annotationRoleName, List<BoRole> boRoles) {
        Optional<BoRole> annotationRole = findFirstAnnotationRole(boRoles, annotationRoleName);
        checkAnnotationRole(minPriorityUserRoles, annotationRole);
    }

    private Optional<BoRole> findFirstAnnotationRole(
            List<BoRole> boRoles, String annotationRoleName) {
        return boRoles.stream()
                .filter(boRole -> boRole.getRoleType().name().equals(annotationRoleName))
                .findFirst();
    }

    private void checkAnnotationRole(int minPriorityUserRoles, Optional<BoRole> annotationRole) {
        annotationRole.ifPresentOrElse(
                aR -> isUserHierarchySuperiorThanAnnotationHierarchy(minPriorityUserRoles, aR),
                () -> {
                    throw new ForbiddenException();
                });
    }

    private void isUserHierarchySuperiorThanAnnotationHierarchy(
            int minPriorityUserRoles, BoRole annotationRole) {
        if (minPriorityUserRoles > annotationRole.getPriority()) {
            throw new ForbiddenException();
        }
    }
}
