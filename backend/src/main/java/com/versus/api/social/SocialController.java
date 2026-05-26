package com.versus.api.social;

import com.versus.api.common.dto.ErrorResponse;
import com.versus.api.match.dto.LobbyStateDto;
import com.versus.api.social.dto.CreateFriendRequest;
import com.versus.api.social.dto.CreateMatchInviteRequest;
import com.versus.api.social.dto.FriendRequestResponse;
import com.versus.api.social.dto.FriendResponse;
import com.versus.api.social.dto.MatchInviteResponse;
import com.versus.api.social.dto.SocialUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Social", description = "Friends, friend requests and match invitations")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @Operation(summary = "Search active users by username")
    @GetMapping("/users/search")
    public List<SocialUserResponse> search(@AuthenticationPrincipal UUID userId,
                                           @RequestParam("query") String query) {
        return socialService.searchUsers(userId, query);
    }

    @Operation(summary = "List authenticated user's friends")
    @GetMapping("/friends")
    public List<FriendResponse> friends(@AuthenticationPrincipal UUID userId) {
        return socialService.listFriends(userId);
    }

    @Operation(summary = "Send a friend request",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Friend request sent"),
                    @ApiResponse(responseCode = "400", description = "Invalid target",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Already friends or pending",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse sendFriendRequest(@AuthenticationPrincipal UUID userId,
                                                   @Valid @RequestBody CreateFriendRequest req) {
        return socialService.sendFriendRequest(userId, req);
    }

    @Operation(summary = "List incoming pending friend requests")
    @GetMapping("/friend-requests/incoming")
    public List<FriendRequestResponse> incomingFriendRequests(@AuthenticationPrincipal UUID userId) {
        return socialService.incomingRequests(userId);
    }

    @Operation(summary = "List outgoing pending friend requests")
    @GetMapping("/friend-requests/outgoing")
    public List<FriendRequestResponse> outgoingFriendRequests(@AuthenticationPrincipal UUID userId) {
        return socialService.outgoingRequests(userId);
    }

    @Operation(summary = "Accept an incoming friend request")
    @PostMapping("/friend-requests/{id}/accept")
    public FriendRequestResponse acceptFriendRequest(@AuthenticationPrincipal UUID userId,
                                                     @PathVariable UUID id) {
        return socialService.acceptFriendRequest(userId, id);
    }

    @Operation(summary = "Decline an incoming friend request")
    @PostMapping("/friend-requests/{id}/decline")
    public FriendRequestResponse declineFriendRequest(@AuthenticationPrincipal UUID userId,
                                                      @PathVariable UUID id) {
        return socialService.declineFriendRequest(userId, id);
    }

    @Operation(summary = "Cancel an outgoing friend request")
    @DeleteMapping("/friend-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelFriendRequest(@AuthenticationPrincipal UUID userId,
                                    @PathVariable UUID id) {
        socialService.cancelFriendRequest(userId, id);
    }

    @Operation(summary = "Create a match lobby and invite a friend",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Invitation sent"),
                    @ApiResponse(responseCode = "403", description = "Target user is not a friend",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/match-invites")
    @ResponseStatus(HttpStatus.CREATED)
    public MatchInviteResponse createMatchInvite(@AuthenticationPrincipal UUID userId,
                                                 @Valid @RequestBody CreateMatchInviteRequest req) {
        return socialService.inviteToMatch(userId, req);
    }

    @Operation(summary = "List incoming pending match invitations")
    @GetMapping("/match-invites/incoming")
    public List<MatchInviteResponse> incomingMatchInvites(@AuthenticationPrincipal UUID userId) {
        return socialService.incomingMatchInvites(userId);
    }

    @Operation(summary = "List recently sent match invitations")
    @GetMapping("/match-invites/outgoing")
    public List<MatchInviteResponse> outgoingMatchInvites(@AuthenticationPrincipal UUID userId) {
        return socialService.outgoingMatchInvites(userId);
    }

    @Operation(summary = "Accept a match invitation and join its lobby")
    @PostMapping("/match-invites/{id}/accept")
    public LobbyStateDto acceptMatchInvite(@AuthenticationPrincipal UUID userId,
                                           @PathVariable UUID id) {
        return socialService.acceptMatchInvite(userId, id);
    }

    @Operation(summary = "Decline a match invitation")
    @PostMapping("/match-invites/{id}/decline")
    public MatchInviteResponse declineMatchInvite(@AuthenticationPrincipal UUID userId,
                                                  @PathVariable UUID id) {
        return socialService.declineMatchInvite(userId, id);
    }
}
