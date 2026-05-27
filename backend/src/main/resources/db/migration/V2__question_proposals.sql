CREATE TABLE public.question_proposals (
    reviewed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    author_id uuid NOT NULL,
    id uuid NOT NULL,
    reviewed_by uuid,
    type character varying(16) NOT NULL,
    status character varying(16) NOT NULL,
    category character varying(64) NOT NULL,
    reject_reason character varying(512),
    source_url character varying(1024),
    proposed_answer character varying(512) NOT NULL,
    text text NOT NULL,
    CONSTRAINT question_proposals_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT question_proposals_type_check CHECK (((type)::text = ANY ((ARRAY['BINARY'::character varying, 'NUMERIC'::character varying])::text[])))
);

ALTER TABLE ONLY public.question_proposals
    ADD CONSTRAINT question_proposals_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.question_proposals
    ADD CONSTRAINT fk_question_proposals_author FOREIGN KEY (author_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.question_proposals
    ADD CONSTRAINT fk_question_proposals_reviewer FOREIGN KEY (reviewed_by) REFERENCES public.users(id);

CREATE INDEX idx_question_proposals_author_created
    ON public.question_proposals USING btree (author_id, created_at DESC);

CREATE INDEX idx_question_proposals_status_created
    ON public.question_proposals USING btree (status, created_at DESC);
