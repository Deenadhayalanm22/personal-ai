package com.apps.deen_sa.service;

import com.apps.deen_sa.exception.WebApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/** Read-through lookup only: MFAPI remains the scheme catalogue, not our database. */
@Service
public class MfApiService {
    private final RestTemplate http;

    public MfApiService(RestTemplate http) { this.http = http; }

    public List<SchemeSearchResult> search(String query) {
        if (query == null || query.trim().length() < 2) {
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHEME_SEARCH", "q must contain at least 2 characters");
        }
        try {
            MfApiScheme[] result = http.getForObject("https://api.mfapi.in/mf/search?q={query}", MfApiScheme[].class, query.trim());
            return result == null ? List.of() : Arrays.stream(result)
                    .map(row -> new SchemeSearchResult(String.valueOf(row.schemeCode()), row.schemeName())).toList();
        } catch (RuntimeException failure) {
            throw new WebApiException(HttpStatus.BAD_GATEWAY, "SCHEME_LOOKUP_FAILED", "Unable to search mutual fund schemes");
        }
    }

    private record MfApiScheme(Long schemeCode, String schemeName) { }
    public record SchemeSearchResult(String schemeCode, String schemeName) { }
}
