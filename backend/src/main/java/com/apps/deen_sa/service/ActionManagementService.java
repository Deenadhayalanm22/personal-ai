package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.UserActionItemStatus;
import com.apps.deen_sa.domain.UserActionItemType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserActionItemEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserActionItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Persistent action queue and its common API boundary. */
@Service
public class ActionManagementService {
    private final UserActionItemRepository actions;
    private final Clock clock;
    private final Map<UserActionItemType, UserActionCompletionHandler> completionHandlers;

    public ActionManagementService(UserActionItemRepository actions, Clock clock,
                                   List<UserActionCompletionHandler> completionHandlers) {
        this.actions = actions;
        this.clock = clock;
        this.completionHandlers = completionHandlers.stream().collect(Collectors.toMap(
                UserActionCompletionHandler::actionType, Function.identity()));
    }

    @Transactional
    public void enqueue(ActionRequest request) {
        if (actions.existsByUserIdAndActionTypeAndReferenceTypeAndReferenceIdAndStatus(
                request.user().getId(), request.actionType(), request.referenceType(), request.referenceId(),
                UserActionItemStatus.OPEN)) return;
        UserActionItemEntity action = new UserActionItemEntity();
        action.setUser(request.user());
        action.setActionType(request.actionType());
        action.setReferenceType(request.referenceType());
        action.setReferenceId(request.referenceId());
        action.setTitle(request.title());
        action.setDescription(request.description());
        action.setScheduledCompletionDate(request.scheduledCompletionDate());
        actions.save(action);
    }

    @Transactional(readOnly = true)
    public ActionListResponse openActions(AppUserEntity user) {
        return new ActionListResponse(actions.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), UserActionItemStatus.OPEN)
                .stream().map(ActionResponse::from).toList());
    }

    @Transactional
    public ActionResponse complete(AppUserEntity user, Long actionId) {
        UserActionItemEntity action = actions.findByIdAndUserIdAndStatus(actionId, user.getId(), UserActionItemStatus.OPEN)
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "ACTION_NOT_FOUND", "Action not found"));
        UserActionCompletionHandler handler = completionHandlers.get(action.getActionType());
        if (handler == null) throw new WebApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_ACTION", "Action is not supported");
        handler.complete(user, action);
        action.setStatus(UserActionItemStatus.COMPLETED);
        action.setResolvedAt(Instant.now(clock));
        return ActionResponse.from(actions.save(action));
    }

    public record ActionRequest(AppUserEntity user, UserActionItemType actionType, String referenceType,
                                Long referenceId, String title, String description,
                                LocalDate scheduledCompletionDate) { }
    public record ActionResponse(Long id, UserActionItemType actionType, String referenceType, Long referenceId,
                                 String title, String description, LocalDate scheduledCompletionDate) {
        static ActionResponse from(UserActionItemEntity item) {
            return new ActionResponse(item.getId(), item.getActionType(), item.getReferenceType(), item.getReferenceId(),
                    item.getTitle(), item.getDescription(), item.getScheduledCompletionDate());
        }
    }
    public record ActionListResponse(List<ActionResponse> actions) { }
}
