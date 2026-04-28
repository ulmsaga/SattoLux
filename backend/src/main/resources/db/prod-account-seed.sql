INSERT INTO app_user (
    user_id,
    password_hash,
    email,
    role_code,
    otp_enabled,
    account_status
) VALUES (
    '__LOGIN_ADMIN__',
    '__LOGIN_ADMIN_PW_HASH__',
    '__LOGIN_ADMIN_EMAIL__',
    'ADMIN',
    0,
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    email = VALUES(email),
    role_code = 'ADMIN',
    otp_enabled = 0,
    account_status = 'ACTIVE',
    failed_login_count = 0,
    locked_until = NULL;

INSERT INTO app_user (
    user_id,
    password_hash,
    email,
    role_code,
    otp_enabled,
    account_status
) VALUES (
    '__LOGIN_USER__',
    '__LOGIN_PW_HASH__',
    '__LOGIN_EMAIL__',
    'USER',
    0,
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    email = VALUES(email),
    role_code = 'USER',
    otp_enabled = 0,
    account_status = 'ACTIVE',
    failed_login_count = 0,
    locked_until = NULL;

UPDATE generation_rule gr
JOIN app_user u ON u.user_seq = gr.user_seq
SET
    gr.day_of_week = 4,
    gr.method_code = 'RANDOM',
    gr.generator_code = 'LOCAL',
    gr.set_count = 5,
    gr.analysis_draw_count = NULL,
    gr.sort_order = 1,
    gr.use_yn = 'Y'
WHERE u.user_id = '__LOGIN_ADMIN__'
  AND gr.sort_order = 1;

INSERT INTO generation_rule (
    user_seq,
    day_of_week,
    method_code,
    generator_code,
    set_count,
    analysis_draw_count,
    sort_order,
    use_yn
)
SELECT
    u.user_seq,
    4,
    'RANDOM',
    'LOCAL',
    5,
    NULL,
    1,
    'Y'
FROM app_user u
WHERE u.user_id = '__LOGIN_ADMIN__'
  AND NOT EXISTS (
      SELECT 1
      FROM generation_rule gr
      WHERE gr.user_seq = u.user_seq
        AND gr.sort_order = 1
  );

UPDATE generation_rule gr
JOIN app_user u ON u.user_seq = gr.user_seq
SET
    gr.day_of_week = 4,
    gr.method_code = 'HOT_NUMBER',
    gr.generator_code = 'CLAUDE',
    gr.set_count = 5,
    gr.analysis_draw_count = 1000,
    gr.sort_order = 2,
    gr.use_yn = 'Y'
WHERE u.user_id = '__LOGIN_ADMIN__'
  AND gr.sort_order = 2;

INSERT INTO generation_rule (
    user_seq,
    day_of_week,
    method_code,
    generator_code,
    set_count,
    analysis_draw_count,
    sort_order,
    use_yn
)
SELECT
    u.user_seq,
    4,
    'HOT_NUMBER',
    'CLAUDE',
    5,
    1000,
    2,
    'Y'
FROM app_user u
WHERE u.user_id = '__LOGIN_ADMIN__'
  AND NOT EXISTS (
      SELECT 1
      FROM generation_rule gr
      WHERE gr.user_seq = u.user_seq
        AND gr.sort_order = 2
  );

UPDATE generation_rule gr
JOIN app_user u ON u.user_seq = gr.user_seq
SET
    gr.day_of_week = 4,
    gr.method_code = 'RANDOM',
    gr.generator_code = 'LOCAL',
    gr.set_count = 5,
    gr.analysis_draw_count = NULL,
    gr.sort_order = 1,
    gr.use_yn = 'Y'
WHERE u.user_id = '__LOGIN_USER__'
  AND gr.sort_order = 1;

INSERT INTO generation_rule (
    user_seq,
    day_of_week,
    method_code,
    generator_code,
    set_count,
    analysis_draw_count,
    sort_order,
    use_yn
)
SELECT
    u.user_seq,
    4,
    'RANDOM',
    'LOCAL',
    5,
    NULL,
    1,
    'Y'
FROM app_user u
WHERE u.user_id = '__LOGIN_USER__'
  AND NOT EXISTS (
      SELECT 1
      FROM generation_rule gr
      WHERE gr.user_seq = u.user_seq
        AND gr.sort_order = 1
  );

UPDATE generation_rule gr
JOIN app_user u ON u.user_seq = gr.user_seq
SET
    gr.day_of_week = 4,
    gr.method_code = 'HOT_NUMBER',
    gr.generator_code = 'CLAUDE',
    gr.set_count = 5,
    gr.analysis_draw_count = 1000,
    gr.sort_order = 2,
    gr.use_yn = 'Y'
WHERE u.user_id = '__LOGIN_USER__'
  AND gr.sort_order = 2;

INSERT INTO generation_rule (
    user_seq,
    day_of_week,
    method_code,
    generator_code,
    set_count,
    analysis_draw_count,
    sort_order,
    use_yn
)
SELECT
    u.user_seq,
    4,
    'HOT_NUMBER',
    'CLAUDE',
    5,
    1000,
    2,
    'Y'
FROM app_user u
WHERE u.user_id = '__LOGIN_USER__'
  AND NOT EXISTS (
      SELECT 1
      FROM generation_rule gr
      WHERE gr.user_seq = u.user_seq
        AND gr.sort_order = 2
  );
