INSERT INTO app_user (
    user_id,
    password_hash,
    email,
    role_code,
    otp_enabled,
    account_status
)
SELECT
    '__LOGIN_USER__',
    '__LOGIN_PW_HASH__',
    '__LOGIN_EMAIL__',
    'ADMIN',
    0,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM app_user
    WHERE user_id = '__LOGIN_USER__'
);

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
        AND gr.day_of_week = 4
        AND gr.method_code = 'RANDOM'
        AND gr.generator_code = 'LOCAL'
        AND gr.sort_order = 1
  );

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
        AND gr.day_of_week = 4
        AND gr.method_code = 'HOT_NUMBER'
        AND gr.generator_code = 'CLAUDE'
        AND gr.sort_order = 2
  );
