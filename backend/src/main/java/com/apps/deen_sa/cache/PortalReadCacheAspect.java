package com.apps.deen_sa.cache;

import com.apps.deen_sa.service.WebAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

/** FIN-EPIC-004: reusable financial reads remain scoped to a freshly authenticated profile. */
@Aspect
@Component
public class PortalReadCacheAspect {
    private final PortalReadCache cache;
    private final WebAuthenticationService authentication;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PortalReadCacheAspect(PortalReadCache cache, WebAuthenticationService authentication,
                                ObjectMapper mapper, Clock clock) {
        this.cache = cache; this.authentication = authentication; this.mapper = mapper; this.clock = clock;
    }

    @Around("execution(public * com.apps.deen_sa.controller.WebFinanceController.*(..)) && @annotation(mapping)")
    public Object read(ProceedingJoinPoint invocation, GetMapping mapping) throws Throwable {
        if (Arrays.stream(mapping.value()).anyMatch(path -> path.startsWith("/auth/")))
            return invocation.proceed();
        Object[] args = invocation.getArgs();
        String token = (String) args[0];
        var user = authentication.authenticate(token); // Never serve cached data before auth.
        var method = ((MethodSignature) invocation.getSignature()).getMethod();
        Object[] safeArgs = args.clone();
        safeArgs[0] = null; // Raw bearer tokens must never enter cache keys or values.
        String identity = com.apps.deen_sa.service.MagicLinkService.hash(
                mapper.writeValueAsString(Arrays.asList(method.toGenericString(),
                        com.apps.deen_sa.service.MagicLinkService.hash(token), user.getId(),
                        user.getTimezone(), user.getCurrency(), user.getLocale(), user.getRole(),
                        LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone()))), safeArgs)));
        return cache.get(identity, mapper.constructType(method.getGenericReturnType()), invocation::proceed);
    }
}
