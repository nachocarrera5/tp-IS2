alter table candidate alter column need_version_id drop not null;
alter table candidate alter column source_result_id drop not null;
alter table candidate add column manual_follow_player_id bigint references player(id);

alter table candidate add constraint uq_candidate_manual_player unique (manual_follow_player_id);
alter table candidate add constraint ck_candidate_origin check (
    (need_version_id is null and source_result_id is null
        and manual_follow_player_id is not null and manual_follow_player_id = player_id)
    or (need_version_id is not null and source_result_id is not null and manual_follow_player_id is null)
);
