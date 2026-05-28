-- Versus — esquema inicial.
-- Generado a partir del modelo JPA (Hibernate ddl-auto=create sobre PostgreSQL 18).
-- A partir de aquí, todo cambio de esquema se hace con nuevas migraciones Vn__*.sql.
-- En producción Hibernate corre con ddl-auto=validate y NO modifica el esquema.

CREATE TABLE public.achievements (
    id uuid NOT NULL,
    achievement_key character varying(80) NOT NULL,
    category character varying(80) NOT NULL,
    icon_key character varying(80) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(500) NOT NULL
);


--
-- Name: cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cards (
    eligible_for_survival boolean NOT NULL,
    is_inverse boolean NOT NULL,
    valor numeric(20,4) NOT NULL,
    scraped_at timestamp(6) with time zone,
    id uuid NOT NULL,
    status character varying(24) NOT NULL,
    unidad character varying(32),
    categoria character varying(64) NOT NULL,
    text_hash character varying(64) NOT NULL,
    subcategoria character varying(128) NOT NULL,
    source_url character varying(1024),
    nombre text NOT NULL,
    CONSTRAINT cards_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: friend_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friend_requests (
    created_at timestamp(6) with time zone NOT NULL,
    responded_at timestamp(6) with time zone,
    addressee_id uuid NOT NULL,
    id uuid NOT NULL,
    requester_id uuid NOT NULL,
    status character varying(16) NOT NULL,
    CONSTRAINT friend_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: friendships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friendships (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_high_id uuid NOT NULL,
    user_low_id uuid NOT NULL
);


--
-- Name: match_answers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_answers (
    deviation double precision,
    is_correct boolean,
    life_delta integer NOT NULL,
    answered_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    round_id uuid NOT NULL,
    user_id uuid NOT NULL,
    answer_given character varying(255)
);


--
-- Name: match_invites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_invites (
    created_at timestamp(6) with time zone NOT NULL,
    responded_at timestamp(6) with time zone,
    from_user_id uuid NOT NULL,
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    status character varying(16) NOT NULL,
    to_user_id uuid NOT NULL,
    mode character varying(32) NOT NULL,
    CONSTRAINT match_invites_mode_check CHECK (((mode)::text = ANY ((ARRAY['SURVIVAL'::character varying, 'PRECISION'::character varying, 'BINARY_DUEL'::character varying, 'PRECISION_DUEL'::character varying, 'SABOTAGE'::character varying])::text[]))),
    CONSTRAINT match_invites_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: match_players; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_players (
    best_streak_in_match integer NOT NULL,
    current_streak integer NOT NULL,
    lives_remaining integer NOT NULL,
    rounds_played integer NOT NULL,
    score integer NOT NULL,
    current_card_a_id uuid,
    current_card_b_id uuid,
    current_round_token uuid,
    match_id uuid NOT NULL,
    result character varying(16),
    user_id uuid NOT NULL,
    CONSTRAINT match_players_result_check CHECK (((result)::text = ANY ((ARRAY['WIN'::character varying, 'LOSS'::character varying, 'DRAW'::character varying, 'ABANDONED'::character varying])::text[])))
);


--
-- Name: match_rounds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_rounds (
    round_number integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    card_a_id uuid,
    card_b_id uuid,
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    question_id uuid
);


--
-- Name: matches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.matches (
    created_at timestamp(6) with time zone NOT NULL,
    finished_at timestamp(6) with time zone,
    id uuid NOT NULL,
    owner_user_id uuid,
    room_code character varying(16),
    status character varying(16) NOT NULL,
    access_type character varying(32) NOT NULL,
    mode character varying(32) NOT NULL,
    CONSTRAINT matches_access_type_check CHECK (((access_type)::text = ANY ((ARRAY['PUBLIC_MATCHMAKING'::character varying, 'PRIVATE_ROOM'::character varying])::text[]))),
    CONSTRAINT matches_mode_check CHECK (((mode)::text = ANY ((ARRAY['SURVIVAL'::character varying, 'PRECISION'::character varying, 'BINARY_DUEL'::character varying, 'PRECISION_DUEL'::character varying, 'SABOTAGE'::character varying])::text[]))),
    CONSTRAINT matches_status_check CHECK (((status)::text = ANY ((ARRAY['WAITING'::character varying, 'IN_PROGRESS'::character varying, 'FINISHED'::character varying])::text[])))
);


--
-- Name: matchmaking_queue; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.matchmaking_queue (
    entered_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    mode character varying(32) NOT NULL,
    CONSTRAINT matchmaking_queue_mode_check CHECK (((mode)::text = ANY ((ARRAY['SURVIVAL'::character varying, 'PRECISION'::character varying, 'BINARY_DUEL'::character varying, 'PRECISION_DUEL'::character varying, 'SABOTAGE'::character varying])::text[])))
);


--
-- Name: media_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_assets (
    created_at timestamp(6) with time zone NOT NULL,
    size_bytes bigint NOT NULL,
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    visibility character varying(16) NOT NULL,
    kind character varying(24) NOT NULL,
    content_type character varying(128) NOT NULL,
    object_key character varying(512) NOT NULL,
    public_url character varying(1024),
    original_filename character varying(255) NOT NULL,
    CONSTRAINT media_assets_kind_check CHECK (((kind)::text = ANY ((ARRAY['IMAGE'::character varying, 'VIDEO'::character varying, 'AUDIO'::character varying, 'DOCUMENT'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT media_assets_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'PRIVATE'::character varying])::text[])))
);


--
-- Name: player_stats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.player_stats (
    avg_deviation double precision,
    avg_score integer,
    best_streak integer NOT NULL,
    current_streak integer NOT NULL,
    games_played integer NOT NULL,
    games_won integer NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    mode character varying(32) NOT NULL,
    CONSTRAINT player_stats_mode_check CHECK (((mode)::text = ANY ((ARRAY['SURVIVAL'::character varying, 'PRECISION'::character varying, 'BINARY_DUEL'::character varying, 'PRECISION_DUEL'::character varying, 'SABOTAGE'::character varying])::text[])))
);


--
-- Name: question_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.question_options (
    is_correct boolean NOT NULL,
    id uuid NOT NULL,
    question_id uuid NOT NULL,
    text character varying(512) NOT NULL
);


--
-- Name: question_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.question_reports (
    created_at timestamp(6) with time zone NOT NULL,
    resolved_at timestamp(6) with time zone,
    id uuid NOT NULL,
    question_id uuid NOT NULL,
    reported_by uuid NOT NULL,
    resolved_by uuid,
    status character varying(16) NOT NULL,
    action character varying(24),
    reason character varying(32) NOT NULL,
    comment character varying(512),
    CONSTRAINT question_reports_action_check CHECK (((action)::text = ANY ((ARRAY['DISMISS'::character varying, 'DELETE_QUESTION'::character varying, 'EDIT_QUESTION'::character varying])::text[]))),
    CONSTRAINT question_reports_reason_check CHECK (((reason)::text = ANY ((ARRAY['WRONG_ANSWER'::character varying, 'OUTDATED'::character varying, 'OFFENSIVE'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT question_reports_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'DISMISSED'::character varying, 'RESOLVED'::character varying])::text[])))
);


--
-- Name: questions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.questions (
    correct_value numeric(20,4),
    tolerance_percent numeric(6,2),
    scraped_at timestamp(6) with time zone,
    id uuid NOT NULL,
    type character varying(16) NOT NULL,
    status character varying(24) NOT NULL,
    unit character varying(32),
    category character varying(64),
    text_hash character varying(64),
    source_url character varying(1024),
    explanation text,
    text text NOT NULL,
    CONSTRAINT questions_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'FLAGGED'::character varying])::text[]))),
    CONSTRAINT questions_type_check CHECK (((type)::text = ANY ((ARRAY['BINARY'::character varying, 'NUMERIC'::character varying])::text[])))
);


--
-- Name: rankings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rankings (
    last_delta integer NOT NULL,
    losses integer NOT NULL,
    rating integer NOT NULL,
    win_streak integer NOT NULL,
    wins integer NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    mode character varying(32) NOT NULL,
    CONSTRAINT rankings_mode_check CHECK (((mode)::text = ANY ((ARRAY['SURVIVAL'::character varying, 'PRECISION'::character varying, 'BINARY_DUEL'::character varying, 'PRECISION_DUEL'::character varying, 'SABOTAGE'::character varying])::text[])))
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    revoked boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(255) NOT NULL
);


--
-- Name: spider_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spider_runs (
    errors integer,
    questions_inserted integer,
    finished_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    spider_id uuid NOT NULL
);


--
-- Name: spiders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spiders (
    last_run_at timestamp(6) with time zone,
    id uuid NOT NULL,
    managed_by uuid,
    status character varying(16) NOT NULL,
    name character varying(128) NOT NULL,
    target_url character varying(1024),
    CONSTRAINT spiders_status_check CHECK (((status)::text = ANY ((ARRAY['IDLE'::character varying, 'RUNNING'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: user_achievements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_achievements (
    unlocked_at timestamp(6) with time zone NOT NULL,
    achievement_id uuid NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    enabled boolean NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    password_reset_token_expiry timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    verification_token_expiry timestamp(6) with time zone,
    id uuid NOT NULL,
    role character varying(16) NOT NULL,
    status character varying(16) NOT NULL,
    password_reset_token character varying(64),
    username character varying(64) NOT NULL,
    verification_token character varying(64),
    avatar_url text,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['PLAYER'::character varying, 'MODERATOR'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DELETED'::character varying])::text[])))
);


--
-- Name: achievements achievements_achievement_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.achievements
    ADD CONSTRAINT achievements_achievement_key_key UNIQUE (achievement_key);


--
-- Name: achievements achievements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.achievements
    ADD CONSTRAINT achievements_pkey PRIMARY KEY (id);


--
-- Name: cards cards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cards
    ADD CONSTRAINT cards_pkey PRIMARY KEY (id);


--
-- Name: cards cards_text_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cards
    ADD CONSTRAINT cards_text_hash_key UNIQUE (text_hash);


--
-- Name: friend_requests friend_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_requests
    ADD CONSTRAINT friend_requests_pkey PRIMARY KEY (id);


--
-- Name: friendships friendships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT friendships_pkey PRIMARY KEY (id);


--
-- Name: matches idx_matches_room_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT idx_matches_room_code UNIQUE (room_code);


--
-- Name: match_answers match_answers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_answers
    ADD CONSTRAINT match_answers_pkey PRIMARY KEY (id);


--
-- Name: match_invites match_invites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_invites
    ADD CONSTRAINT match_invites_pkey PRIMARY KEY (id);


--
-- Name: match_players match_players_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_players
    ADD CONSTRAINT match_players_pkey PRIMARY KEY (match_id, user_id);


--
-- Name: match_rounds match_rounds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_rounds
    ADD CONSTRAINT match_rounds_pkey PRIMARY KEY (id);


--
-- Name: matches matches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT matches_pkey PRIMARY KEY (id);


--
-- Name: matchmaking_queue matchmaking_queue_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matchmaking_queue
    ADD CONSTRAINT matchmaking_queue_pkey PRIMARY KEY (id);


--
-- Name: media_assets media_assets_object_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_assets
    ADD CONSTRAINT media_assets_object_key_key UNIQUE (object_key);


--
-- Name: media_assets media_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_assets
    ADD CONSTRAINT media_assets_pkey PRIMARY KEY (id);


--
-- Name: player_stats player_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.player_stats
    ADD CONSTRAINT player_stats_pkey PRIMARY KEY (id);


--
-- Name: question_options question_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_options
    ADD CONSTRAINT question_options_pkey PRIMARY KEY (id);


--
-- Name: question_reports question_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_reports
    ADD CONSTRAINT question_reports_pkey PRIMARY KEY (id);


--
-- Name: questions questions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.questions
    ADD CONSTRAINT questions_pkey PRIMARY KEY (id);


--
-- Name: questions questions_text_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.questions
    ADD CONSTRAINT questions_text_hash_key UNIQUE (text_hash);


--
-- Name: rankings rankings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rankings
    ADD CONSTRAINT rankings_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: spider_runs spider_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spider_runs
    ADD CONSTRAINT spider_runs_pkey PRIMARY KEY (id);


--
-- Name: spiders spiders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spiders
    ADD CONSTRAINT spiders_pkey PRIMARY KEY (id);


--
-- Name: friendships uk_friendship_pair; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT uk_friendship_pair UNIQUE (user_low_id, user_high_id);


--
-- Name: player_stats uk_player_stats_user_mode; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.player_stats
    ADD CONSTRAINT uk_player_stats_user_mode UNIQUE (user_id, mode);


--
-- Name: rankings uk_rankings_user_mode; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rankings
    ADD CONSTRAINT uk_rankings_user_mode UNIQUE (user_id, mode);


--
-- Name: user_achievements user_achievements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_achievements
    ADD CONSTRAINT user_achievements_pkey PRIMARY KEY (achievement_id, user_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: idx_achievements_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_achievements_category ON public.achievements USING btree (category);


--
-- Name: idx_cards_cat_subcat_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cards_cat_subcat_status ON public.cards USING btree (categoria, subcategoria, status);


--
-- Name: idx_cards_status_eligible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cards_status_eligible ON public.cards USING btree (status, eligible_for_survival);


--
-- Name: idx_friend_requests_addressee_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friend_requests_addressee_status ON public.friend_requests USING btree (addressee_id, status, created_at);


--
-- Name: idx_friend_requests_requester_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friend_requests_requester_status ON public.friend_requests USING btree (requester_id, status, created_at);


--
-- Name: idx_friendships_user_high; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friendships_user_high ON public.friendships USING btree (user_high_id);


--
-- Name: idx_friendships_user_low; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friendships_user_low ON public.friendships USING btree (user_low_id);


--
-- Name: idx_match_answers_round; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_match_answers_round ON public.match_answers USING btree (round_id);


--
-- Name: idx_match_answers_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_match_answers_user ON public.match_answers USING btree (user_id);


--
-- Name: idx_match_invites_from_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_match_invites_from_created ON public.match_invites USING btree (from_user_id, created_at);


--
-- Name: idx_match_invites_match; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_match_invites_match ON public.match_invites USING btree (match_id);


--
-- Name: idx_match_invites_to_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_match_invites_to_status ON public.match_invites USING btree (to_user_id, status, created_at);


--
-- Name: idx_match_rounds_match; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_match_rounds_match ON public.match_rounds USING btree (match_id);


--
-- Name: idx_media_assets_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_media_assets_owner ON public.media_assets USING btree (owner_id);


--
-- Name: idx_mmqueue_mode_entered; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mmqueue_mode_entered ON public.matchmaking_queue USING btree (mode, entered_at);


--
-- Name: idx_questions_status_type_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_questions_status_type_category ON public.questions USING btree (status, type, category);


--
-- Name: idx_rankings_mode_rating; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rankings_mode_rating ON public.rankings USING btree (mode, rating DESC, wins DESC);


--
-- Name: idx_rankings_user_mode; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rankings_user_mode ON public.rankings USING btree (user_id, mode);


--
-- Name: idx_refresh_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_hash ON public.refresh_tokens USING btree (token_hash);


--
-- Name: idx_refresh_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_user ON public.refresh_tokens USING btree (user_id);


--
-- Name: user_achievements fk8ipvec6cs8t3g8515thtlsxuf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_achievements
    ADD CONSTRAINT fk8ipvec6cs8t3g8515thtlsxuf FOREIGN KEY (achievement_id) REFERENCES public.achievements(id);


--
-- Name: question_options fksb9v00wdrgc9qojtjkv7e1gkp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_options
    ADD CONSTRAINT fksb9v00wdrgc9qojtjkv7e1gkp FOREIGN KEY (question_id) REFERENCES public.questions(id);
