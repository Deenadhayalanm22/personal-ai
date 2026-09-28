package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** FIN-EPIC-007 — private v2 preview ownership, independent of the active demo profile. */
@Service
public class V2AccessService {
    private final WebAuthenticationService authentication;
    private final WebLoginRequestService loginRequests;
    private final String ownerPhone;
    private final String baseUrl;

    public V2AccessService(WebAuthenticationService authentication, WebLoginRequestService loginRequests,
            @Value("${app.v2.owner-phone:}") String ownerPhone,
            @Value("${app.v2.base-url:}") String baseUrl) {
        this.authentication = authentication;
        this.loginRequests = loginRequests;
        this.ownerPhone = digits(ownerPhone);
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public void requestLink(String phoneNumber, String remoteAddress) {
        loginRequests.requestV2(phoneNumber, remoteAddress, ownerPhone, baseUrl);
    }

    public void requireOwner(String sessionToken) {
        AppUserEntity owner = authentication.authenticateOwner(sessionToken);
        if (ownerPhone.isBlank() || baseUrl.isBlank() || !owner.isPortalEnabled()
                || !"WHATSAPP".equals(owner.getChannel())
                || !ownerPhone.equals(digits(owner.getExternalUserId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "V2 preview access is restricted");
        }
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
