package com.apps.deen_sa.web;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebUserReferenceEntityTypeService {

    public UserReferenceEntityTypesResponse options() {
        return new UserReferenceEntityTypesResponse(List.of(UserReferenceEntityType.values()));
    }

    public record UserReferenceEntityTypesResponse(
            List<UserReferenceEntityType> entityTypes) { }
}
