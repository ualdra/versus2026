package com.versus.api.social;

import com.versus.api.achievements.AchievementCatalog;
import com.versus.api.achievements.AchievementService;
import com.versus.api.common.exception.ApiException;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchService;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.dto.LobbyStateDto;
import com.versus.api.match.state.LiveMatchState;
import com.versus.api.social.domain.FriendRequest;
import com.versus.api.social.domain.Friendship;
import com.versus.api.social.domain.MatchInvite;
import com.versus.api.social.dto.CreateFriendRequest;
import com.versus.api.social.dto.CreateMatchInviteRequest;
import com.versus.api.social.dto.FriendRequestEvent;
import com.versus.api.social.dto.FriendRequestResponse;
import com.versus.api.social.dto.FriendResponse;
import com.versus.api.social.dto.MatchInviteEvent;
import com.versus.api.social.dto.MatchInviteResponse;
import com.versus.api.social.dto.SocialEventEnvelope;
import com.versus.api.social.dto.SocialUserResponse;
import com.versus.api.social.repo.FriendRequestRepository;
import com.versus.api.social.repo.FriendshipRepository;
import com.versus.api.social.repo.MatchInviteRepository;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocialService {

    private static final int MAX_SEARCH_RESULTS = 10;

    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final FriendRequestRepository friendRequests;
    private final MatchInviteRepository matchInvites;
    private final MatchService matchService;
    private final AchievementService achievementService;
    private final SimpMessagingTemplate messaging;

    @Transactional(readOnly = true)
    public List<SocialUserResponse> searchUsers(UUID userId, String query) {
        activeUser(userId);
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        return users.findByUsernameContainingIgnoreCaseAndIsActiveTrue(q, PageRequest.of(0, MAX_SEARCH_RESULTS))
                .stream()
                .filter(user -> !user.getId().equals(userId))
                .map(user -> userSummary(user, relation(userId, user.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(UUID userId) {
        activeUser(userId);
        List<Friendship> links = friendships.findByUserLowIdOrUserHighId(userId, userId);
        Map<UUID, Friendship> byFriendId = links.stream()
                .collect(Collectors.toMap(link -> otherUserId(link, userId), Function.identity()));
        Map<UUID, User> userMap = users.findAllById(byFriendId.keySet()).stream()
                .filter(this::isActive)
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return byFriendId.entrySet().stream()
                .map(entry -> toFriendResponse(userMap.get(entry.getKey()), entry.getValue().getCreatedAt()))
                .filter(friend -> friend != null)
                .sorted(Comparator.comparing(FriendResponse::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> incomingRequests(UUID userId) {
        activeUser(userId);
        return friendRequests.findByAddresseeIdAndStatusOrderByCreatedAtDesc(userId, SocialStatus.PENDING)
                .stream()
                .map(req -> toFriendRequestResponse(req, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> outgoingRequests(UUID userId) {
        activeUser(userId);
        return friendRequests.findByRequesterIdAndStatusOrderByCreatedAtDesc(userId, SocialStatus.PENDING)
                .stream()
                .map(req -> toFriendRequestResponse(req, userId))
                .toList();
    }

    @Transactional
    public FriendRequestResponse sendFriendRequest(UUID userId, CreateFriendRequest req) {
        User requester = activeUser(userId);
        User addressee = activeUser(req.toUserId());
        if (requester.getId().equals(addressee.getId())) {
            throw ApiException.validation("Cannot send a friend request to yourself");
        }
        if (areFriends(requester.getId(), addressee.getId())) {
            throw ApiException.conflict("Users are already friends");
        }
        friendRequests.findPendingBetween(requester.getId(), addressee.getId(), SocialStatus.PENDING)
                .ifPresent(existing -> {
                    throw ApiException.conflict("A friend request already exists between these users");
                });

        FriendRequest created = friendRequests.save(FriendRequest.builder()
                .requesterId(requester.getId())
                .addresseeId(addressee.getId())
                .status(SocialStatus.PENDING)
                .build());
        messaging.convertAndSendToUser(
                addressee.getId().toString(),
                "/queue/social",
                SocialEventEnvelope.of("FRIEND_REQUEST",
                        new FriendRequestEvent(created.getId(), userSummary(requester, SocialRelation.REQUEST_RECEIVED))));
        return toFriendRequestResponse(created, userId);
    }

    @Transactional
    public FriendRequestResponse acceptFriendRequest(UUID userId, UUID requestId) {
        activeUser(userId);
        FriendRequest request = friendRequests.findByIdAndAddresseeId(requestId, userId)
                .orElseThrow(() -> ApiException.notFound("Friend request not found"));
        requirePending(request);
        request.setStatus(SocialStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());
        createFriendship(request.getRequesterId(), request.getAddresseeId());
        friendRequests.save(request);
        unlockSocialFriendAchievements(request.getRequesterId(), request.getAddresseeId());
        return toFriendRequestResponse(request, userId);
    }

    @Transactional
    public FriendRequestResponse declineFriendRequest(UUID userId, UUID requestId) {
        activeUser(userId);
        FriendRequest request = friendRequests.findByIdAndAddresseeId(requestId, userId)
                .orElseThrow(() -> ApiException.notFound("Friend request not found"));
        requirePending(request);
        request.setStatus(SocialStatus.DECLINED);
        request.setRespondedAt(Instant.now());
        return toFriendRequestResponse(friendRequests.save(request), userId);
    }

    @Transactional
    public void cancelFriendRequest(UUID userId, UUID requestId) {
        activeUser(userId);
        FriendRequest request = friendRequests.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> ApiException.notFound("Friend request not found"));
        requirePending(request);
        request.setStatus(SocialStatus.CANCELLED);
        request.setRespondedAt(Instant.now());
        friendRequests.save(request);
    }

    @Transactional(readOnly = true)
    public List<MatchInviteResponse> incomingMatchInvites(UUID userId) {
        activeUser(userId);
        return matchInvites.findByToUserIdAndStatusOrderByCreatedAtDesc(userId, SocialStatus.PENDING)
                .stream()
                .map(invite -> toMatchInviteResponse(invite, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchInviteResponse> outgoingMatchInvites(UUID userId) {
        activeUser(userId);
        return matchInvites.findByFromUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(10)
                .map(invite -> toMatchInviteResponse(invite, userId))
                .toList();
    }

    @Transactional
    public MatchInviteResponse inviteToMatch(UUID userId, CreateMatchInviteRequest req) {
        User sender = activeUser(userId);
        User friend = activeUser(req.friendUserId());
        if (!req.mode().isMultiplayer()) {
            throw ApiException.validation("Only multiplayer modes can be invited");
        }
        if (!areFriends(sender.getId(), friend.getId())) {
            throw ApiException.forbidden("Only friends can be invited");
        }

        LiveMatchState state = req.matchId() == null
                ? createInviteMatch(req.mode(), sender.getId())
                : requireInvitableMatch(req.matchId(), req.mode(), sender.getId(), friend.getId());
        if (matchInvites.existsByMatchIdAndToUserIdAndStatus(
                state.getMatchId(), friend.getId(), SocialStatus.PENDING)) {
            throw ApiException.conflict("A pending invite already exists for this match");
        }

        MatchInvite invite = matchInvites.save(MatchInvite.builder()
                .matchId(state.getMatchId())
                .fromUserId(sender.getId())
                .toUserId(friend.getId())
                .mode(state.getMode())
                .status(SocialStatus.PENDING)
                .build());
        achievementService.unlockByKey(sender.getId(), AchievementCatalog.SOCIAL_INVITE);
        messaging.convertAndSendToUser(
                friend.getId().toString(),
                "/queue/social",
                SocialEventEnvelope.of("MATCH_INVITE",
                        new MatchInviteEvent(invite.getId(), invite.getMatchId(), invite.getMode(),
                                userSummary(sender, SocialRelation.FRIEND))));
        return toMatchInviteResponse(invite, userId);
    }

    private LiveMatchState createInviteMatch(GameMode mode, UUID senderId) {
        LiveMatchState state = matchService.createMatch(mode, senderId);
        matchService.addPlayer(state.getMatchId(), senderId);
        return state;
    }

    private LiveMatchState requireInvitableMatch(UUID matchId, GameMode mode, UUID senderId, UUID friendId) {
        LiveMatchState state = matchService.requireLive(matchId);
        synchronized (state) {
            if (state.getMode() != mode) {
                throw ApiException.validation("Invite mode does not match the match mode");
            }
            if (state.getStatus() != MatchStatus.WAITING) {
                throw ApiException.conflict("Match is not accepting invites");
            }
            if (!state.getPlayers().containsKey(senderId)) {
                throw ApiException.forbidden("Only match players can invite friends");
            }
            if (state.getPlayers().containsKey(friendId)) {
                throw ApiException.conflict("User is already in this match");
            }
            if (state.isFull()) {
                throw ApiException.conflict("Match is full");
            }
        }
        return state;
    }

    @Transactional
    public LobbyStateDto acceptMatchInvite(UUID userId, UUID inviteId) {
        activeUser(userId);
        MatchInvite invite = matchInvites.findByIdAndToUserId(inviteId, userId)
                .orElseThrow(() -> ApiException.notFound("Match invite not found"));
        requirePending(invite);
        LiveMatchState state = matchService.addPlayer(invite.getMatchId(), userId);
        invite.setStatus(SocialStatus.ACCEPTED);
        invite.setRespondedAt(Instant.now());
        matchInvites.save(invite);
        return matchService.toLobbyDto(state);
    }

    @Transactional
    public MatchInviteResponse declineMatchInvite(UUID userId, UUID inviteId) {
        activeUser(userId);
        MatchInvite invite = matchInvites.findByIdAndToUserId(inviteId, userId)
                .orElseThrow(() -> ApiException.notFound("Match invite not found"));
        requirePending(invite);
        invite.setStatus(SocialStatus.DECLINED);
        invite.setRespondedAt(Instant.now());
        return toMatchInviteResponse(matchInvites.save(invite), userId);
    }

    private void createFriendship(UUID userA, UUID userB) {
        UUID[] pair = orderedPair(userA, userB);
        if (friendships.existsByUserLowIdAndUserHighId(pair[0], pair[1])) {
            return;
        }
        friendships.save(Friendship.builder()
                .userLowId(pair[0])
                .userHighId(pair[1])
                .build());
    }

    private void unlockSocialFriendAchievements(UUID userA, UUID userB) {
        if (friendships.countByUserLowIdOrUserHighId(userA, userA) >= 3) {
            achievementService.unlockByKey(userA, AchievementCatalog.SOCIAL_3_FRIENDS);
        }
        if (friendships.countByUserLowIdOrUserHighId(userB, userB) >= 3) {
            achievementService.unlockByKey(userB, AchievementCatalog.SOCIAL_3_FRIENDS);
        }
    }

    private FriendRequestResponse toFriendRequestResponse(FriendRequest request, UUID viewerId) {
        Map<UUID, User> userMap = users.findAllById(List.of(request.getRequesterId(), request.getAddresseeId()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new FriendRequestResponse(
                request.getId(),
                userSummary(userMap.get(request.getRequesterId()), relation(viewerId, request.getRequesterId())),
                userSummary(userMap.get(request.getAddresseeId()), relation(viewerId, request.getAddresseeId())),
                request.getStatus(),
                request.getCreatedAt(),
                request.getRespondedAt());
    }

    private MatchInviteResponse toMatchInviteResponse(MatchInvite invite, UUID viewerId) {
        Map<UUID, User> userMap = users.findAllById(List.of(invite.getFromUserId(), invite.getToUserId()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new MatchInviteResponse(
                invite.getId(),
                invite.getMatchId(),
                invite.getMode(),
                userSummary(userMap.get(invite.getFromUserId()), relation(viewerId, invite.getFromUserId())),
                userSummary(userMap.get(invite.getToUserId()), relation(viewerId, invite.getToUserId())),
                invite.getStatus(),
                invite.getCreatedAt(),
                invite.getRespondedAt());
    }

    private FriendResponse toFriendResponse(User user, Instant friendsSince) {
        if (user == null) return null;
        return new FriendResponse(user.getId(), user.getUsername(), user.getAvatarUrl(), friendsSince);
    }

    private SocialUserResponse userSummary(User user, SocialRelation relation) {
        if (user == null) {
            return new SocialUserResponse(null, "Unknown", null, relation);
        }
        return new SocialUserResponse(user.getId(), user.getUsername(), user.getAvatarUrl(), relation);
    }

    private SocialRelation relation(UUID viewerId, UUID otherId) {
        if (viewerId.equals(otherId)) {
            return SocialRelation.SELF;
        }
        if (areFriends(viewerId, otherId)) {
            return SocialRelation.FRIEND;
        }
        return friendRequests.findPendingBetween(viewerId, otherId, SocialStatus.PENDING)
                .map(request -> request.getRequesterId().equals(viewerId)
                        ? SocialRelation.REQUEST_SENT
                        : SocialRelation.REQUEST_RECEIVED)
                .orElse(SocialRelation.NONE);
    }

    private boolean areFriends(UUID userA, UUID userB) {
        UUID[] pair = orderedPair(userA, userB);
        return friendships.existsByUserLowIdAndUserHighId(pair[0], pair[1]);
    }

    private UUID[] orderedPair(UUID userA, UUID userB) {
        return userA.compareTo(userB) <= 0
                ? new UUID[]{userA, userB}
                : new UUID[]{userB, userA};
    }

    private UUID otherUserId(Friendship friendship, UUID userId) {
        return friendship.getUserLowId().equals(userId)
                ? friendship.getUserHighId()
                : friendship.getUserLowId();
    }

    private void requirePending(FriendRequest request) {
        if (request.getStatus() != SocialStatus.PENDING) {
            throw ApiException.conflict("Friend request is not pending");
        }
    }

    private void requirePending(MatchInvite invite) {
        if (invite.getStatus() != SocialStatus.PENDING) {
            throw ApiException.conflict("Match invite is not pending");
        }
    }

    private User activeUser(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!isActive(user)) {
            throw ApiException.notFound("User not found");
        }
        return user;
    }

    private boolean isActive(User user) {
        return user != null
                && !UserStatus.DELETED.equals(user.getStatus())
                && !Boolean.FALSE.equals(user.getIsActive());
    }
}
