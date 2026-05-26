package com.versus.api.social;

import com.versus.api.achievements.AchievementCatalog;
import com.versus.api.achievements.AchievementService;
import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchService;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.dto.LobbyStateDto;
import com.versus.api.match.state.LiveMatchState;
import com.versus.api.match.state.LivePlayerState;
import com.versus.api.social.domain.FriendRequest;
import com.versus.api.social.domain.Friendship;
import com.versus.api.social.domain.MatchInvite;
import com.versus.api.social.dto.CreateFriendRequest;
import com.versus.api.social.dto.CreateMatchInviteRequest;
import com.versus.api.social.dto.SocialEventEnvelope;
import com.versus.api.social.repo.FriendRequestRepository;
import com.versus.api.social.repo.FriendshipRepository;
import com.versus.api.social.repo.MatchInviteRepository;
import com.versus.api.users.Role;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SocialService")
@ExtendWith(MockitoExtension.class)
class SocialServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID FRIEND_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final UUID REQUEST_ID = UUID.fromString("cccc0000-0000-0000-0000-000000000003");
    private static final UUID INVITE_ID = UUID.fromString("dddd0000-0000-0000-0000-000000000004");
    private static final UUID MATCH_ID = UUID.fromString("eeee0000-0000-0000-0000-000000000005");

    @Mock UserRepository users;
    @Mock FriendshipRepository friendships;
    @Mock FriendRequestRepository friendRequests;
    @Mock MatchInviteRepository matchInvites;
    @Mock MatchService matchService;
    @Mock AchievementService achievementService;
    @Mock SimpMessagingTemplate messaging;

    @InjectMocks SocialService socialService;

    private final User self = user(USER_ID, "self");
    private final User friend = user(FRIEND_ID, "friend");

    @BeforeEach
    void setup() {
        lenient().when(users.findById(USER_ID)).thenReturn(Optional.of(self));
        lenient().when(users.findById(FRIEND_ID)).thenReturn(Optional.of(friend));
        lenient().when(users.findAllById(any())).thenReturn(List.of(self, friend));
        lenient().when(friendRequests.findPendingBetween(any(), any(), eq(SocialStatus.PENDING)))
                .thenReturn(Optional.empty());
        lenient().when(friendRequests.save(any(FriendRequest.class))).thenAnswer(inv -> {
            FriendRequest request = inv.getArgument(0);
            if (request.getId() == null) request.setId(REQUEST_ID);
            if (request.getCreatedAt() == null) request.setCreatedAt(Instant.now());
            return request;
        });
        lenient().when(friendships.save(any(Friendship.class))).thenAnswer(inv -> {
            Friendship friendship = inv.getArgument(0);
            if (friendship.getId() == null) friendship.setId(UUID.randomUUID());
            if (friendship.getCreatedAt() == null) friendship.setCreatedAt(Instant.now());
            return friendship;
        });
        lenient().when(matchInvites.save(any(MatchInvite.class))).thenAnswer(inv -> {
            MatchInvite invite = inv.getArgument(0);
            if (invite.getId() == null) invite.setId(INVITE_ID);
            if (invite.getCreatedAt() == null) invite.setCreatedAt(Instant.now());
            return invite;
        });
    }

    @Nested
    @DisplayName("sendFriendRequest")
    class SendFriendRequest {

        @Test
        @DisplayName("crea solicitud pendiente y notifica al destinatario")
        void creaSolicitudYNotifica() {
            var result = socialService.sendFriendRequest(USER_ID, new CreateFriendRequest(FRIEND_ID));

            assertThat(result.id()).isEqualTo(REQUEST_ID);
            assertThat(result.status()).isEqualTo(SocialStatus.PENDING);
            verify(friendRequests).save(argThat(req ->
                    req.getRequesterId().equals(USER_ID)
                            && req.getAddresseeId().equals(FRIEND_ID)
                            && req.getStatus() == SocialStatus.PENDING));
            ArgumentCaptor<SocialEventEnvelope> event = ArgumentCaptor.forClass(SocialEventEnvelope.class);
            verify(messaging).convertAndSendToUser(eq(FRIEND_ID.toString()), eq("/queue/social"), event.capture());
            assertThat(event.getValue().type()).isEqualTo("FRIEND_REQUEST");
        }

        @Test
        @DisplayName("rechaza solicitud a uno mismo")
        void rechazaSelf() {
            assertThatThrownBy(() -> socialService.sendFriendRequest(USER_ID, new CreateFriendRequest(USER_ID)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @Test
        @DisplayName("rechaza si ya son amigos")
        void rechazaAmigosExistentes() {
            when(friendships.existsByUserLowIdAndUserHighId(any(), any())).thenReturn(true);

            assertThatThrownBy(() -> socialService.sendFriendRequest(USER_ID, new CreateFriendRequest(FRIEND_ID)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT));
        }
    }

    @Nested
    @DisplayName("acceptFriendRequest")
    class AcceptFriendRequest {

        @Test
        @DisplayName("acepta, crea amistad y evalua logro social")
        void aceptaYCreaAmistad() {
            FriendRequest request = FriendRequest.builder()
                    .id(REQUEST_ID)
                    .requesterId(FRIEND_ID)
                    .addresseeId(USER_ID)
                    .status(SocialStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
            when(friendRequests.findByIdAndAddresseeId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(request));
            when(friendships.countByUserLowIdOrUserHighId(any(), any())).thenReturn(3L);

            var result = socialService.acceptFriendRequest(USER_ID, REQUEST_ID);

            assertThat(result.status()).isEqualTo(SocialStatus.ACCEPTED);
            verify(friendships).save(any(Friendship.class));
            verify(achievementService).unlockByKey(USER_ID, AchievementCatalog.SOCIAL_3_FRIENDS);
            verify(achievementService).unlockByKey(FRIEND_ID, AchievementCatalog.SOCIAL_3_FRIENDS);
        }
    }

    @Nested
    @DisplayName("inviteToMatch")
    class InviteToMatch {

        @Test
        @DisplayName("crea lobby, guarda invitacion y notifica al amigo")
        void creaLobbyYNotifica() {
            when(friendships.existsByUserLowIdAndUserHighId(any(), any())).thenReturn(true);
            LiveMatchState state = LiveMatchState.builder()
                    .matchId(MATCH_ID)
                    .mode(GameMode.BINARY_DUEL)
                    .roomCode("ABC123")
                    .createdAt(Instant.now())
                    .build();
            when(matchService.createMatch(GameMode.BINARY_DUEL, USER_ID)).thenReturn(state);
            when(matchService.addPlayer(MATCH_ID, USER_ID)).thenReturn(state);

            var result = socialService.inviteToMatch(
                    USER_ID,
                    new CreateMatchInviteRequest(FRIEND_ID, GameMode.BINARY_DUEL));

            assertThat(result.matchId()).isEqualTo(MATCH_ID);
            verify(matchService).createMatch(GameMode.BINARY_DUEL, USER_ID);
            verify(matchService).addPlayer(MATCH_ID, USER_ID);
            verify(achievementService).unlockByKey(USER_ID, AchievementCatalog.SOCIAL_INVITE);
            ArgumentCaptor<SocialEventEnvelope> event = ArgumentCaptor.forClass(SocialEventEnvelope.class);
            verify(messaging).convertAndSendToUser(eq(FRIEND_ID.toString()), eq("/queue/social"), event.capture());
            assertThat(event.getValue().type()).isEqualTo("MATCH_INVITE");
        }

        @Test
        @DisplayName("usa una sala privada existente para invitar al amigo")
        void usaSalaPrivadaExistente() {
            when(friendships.existsByUserLowIdAndUserHighId(any(), any())).thenReturn(true);
            LiveMatchState state = LiveMatchState.builder()
                    .matchId(MATCH_ID)
                    .mode(GameMode.BINARY_DUEL)
                    .roomCode("ABC123")
                    .status(MatchStatus.WAITING)
                    .createdAt(Instant.now())
                    .build();
            state.getPlayers().put(USER_ID, LivePlayerState.builder()
                    .userId(USER_ID)
                    .username("self")
                    .ready(false)
                    .build());
            when(matchService.requireLive(MATCH_ID)).thenReturn(state);

            var result = socialService.inviteToMatch(
                    USER_ID,
                    new CreateMatchInviteRequest(FRIEND_ID, GameMode.BINARY_DUEL, MATCH_ID));

            assertThat(result.matchId()).isEqualTo(MATCH_ID);
            verify(matchService, never()).createMatch(any(), any());
            verify(matchService, never()).addPlayer(MATCH_ID, USER_ID);
            verify(matchInvites).save(argThat(invite ->
                    MATCH_ID.equals(invite.getMatchId())
                            && FRIEND_ID.equals(invite.getToUserId())
                            && invite.getMode() == GameMode.BINARY_DUEL));
        }

        @Test
        @DisplayName("rechaza invitacion a usuario que no es amigo")
        void rechazaNoAmigo() {
            when(friendships.existsByUserLowIdAndUserHighId(any(), any())).thenReturn(false);

            assertThatThrownBy(() -> socialService.inviteToMatch(
                    USER_ID,
                    new CreateMatchInviteRequest(FRIEND_ID, GameMode.BINARY_DUEL)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }
    }

    @Test
    @DisplayName("acceptMatchInvite une al invitado y devuelve lobby")
    void acceptMatchInviteUneYDevuelveLobby() {
        MatchInvite invite = MatchInvite.builder()
                .id(INVITE_ID)
                .matchId(MATCH_ID)
                .fromUserId(FRIEND_ID)
                .toUserId(USER_ID)
                .mode(GameMode.BINARY_DUEL)
                .status(SocialStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        LiveMatchState state = LiveMatchState.builder()
                .matchId(MATCH_ID)
                .mode(GameMode.BINARY_DUEL)
                .roomCode("ABC123")
                .status(MatchStatus.WAITING)
                .createdAt(Instant.now())
                .build();
        LobbyStateDto lobby = new LobbyStateDto(MATCH_ID, GameMode.BINARY_DUEL, MatchStatus.WAITING, List.of(), 2, "ABC123");
        when(matchInvites.findByIdAndToUserId(INVITE_ID, USER_ID)).thenReturn(Optional.of(invite));
        when(matchService.addPlayer(MATCH_ID, USER_ID)).thenReturn(state);
        when(matchService.toLobbyDto(state)).thenReturn(lobby);

        LobbyStateDto result = socialService.acceptMatchInvite(USER_ID, INVITE_ID);

        assertThat(result.matchId()).isEqualTo(MATCH_ID);
        assertThat(invite.getStatus()).isEqualTo(SocialStatus.ACCEPTED);
        verify(matchInvites).save(invite);
    }

    private User user(UUID id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@versus.test")
                .passwordHash("hash")
                .role(Role.PLAYER)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }
}
