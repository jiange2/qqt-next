-- 设备快照排序 E2E 夹具：8 特征设备 + 5 填充 = 13（size=10 时 2 页）
-- song 999999 duration=3600 → 在线窗口 +61 分钟；在线设备 last 在 -5s~-30s，离线在 -2h~-7h
-- 每设备先插旧事实（first_seen），最后插最新事实（携带快照 budget/usage 与 last_seen，MAX(id) 即最新）
SET @now := UTC_TIMESTAMP();

INSERT INTO songs (id, type, title, audio_url, thumbnail, description, duration)
VALUES (999999, 'local', 'E2E Fixture', 'fixture.mp3', 'fixture.jpg', 'sort e2e fixture', 3600);

-- devA: 4 facts, budget 3GB, usage 2GB, first -40d, last -10s, ONLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'devA',0,1073741824,524288000,'1.0.0','10.0.0.1','QQT/1.0 (Android 34; Test)', @now - INTERVAL 40 DAY),
 (999999,'devA',1,1073741824,524288000,'1.0.0','10.0.0.1','QQT/1.0 (Android 34; Test)', @now - INTERVAL 40 DAY),
 (999999,'devA',0,2147483648,1073741824,'1.0.0','10.0.0.1','QQT/1.0 (Android 34; Test)', @now - INTERVAL 40 DAY),
 (999999,'devA',1,3221225472,2147483648,'1.2.0','10.0.0.1','QQT/1.2 (Android 34; Test)', @now - INTERVAL 10 SECOND);

-- devB: 2 facts, budget 1GB, usage 3GB, first -10d, last -2h, OFFLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'devB',0,524288000,262144000,'1.0.0','10.0.0.2','QQT/1.0 (Android 33; Test)', @now - INTERVAL 10 DAY),
 (999999,'devB',1,1073741824,3221225472,'1.1.0','10.0.0.2','QQT/1.1 (Android 33; Test)', @now - INTERVAL 2 HOUR);

-- devC: 2 facts, budget NULL, usage NULL, first -2d, last -5s, ONLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'devC',0,NULL,NULL,NULL,'10.0.0.3','QQT/1.0 (Android 34; Test)', @now - INTERVAL 2 DAY),
 (999999,'devC',0,NULL,NULL,NULL,'10.0.0.3','QQT/1.0 (Android 34; Test)', @now - INTERVAL 5 SECOND);

-- devD: 6 facts, budget 1GB, usage 500MB, first -60d, last -3h, OFFLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'devD',0,262144000,131072000,'1.0.0','10.0.0.4','QQT/1.0 (Android 32; Test)', @now - INTERVAL 60 DAY),
 (999999,'devD',1,262144000,131072000,'1.0.0','10.0.0.4','QQT/1.0 (Android 32; Test)', @now - INTERVAL 60 DAY),
 (999999,'devD',0,524288000,262144000,'1.0.0','10.0.0.4','QQT/1.0 (Android 32; Test)', @now - INTERVAL 60 DAY),
 (999999,'devD',1,524288000,262144000,'1.0.0','10.0.0.4','QQT/1.0 (Android 32; Test)', @now - INTERVAL 60 DAY),
 (999999,'devD',0,1073741824,524288000,'1.0.0','10.0.0.4','QQT/1.0 (Android 32; Test)', @now - INTERVAL 60 DAY),
 (999999,'devD',1,1073741824,524288000,'1.0.0','10.0.0.4','QQT/1.0 (Android 32; Test)', @now - INTERVAL 3 HOUR);

-- devE: 3 facts, budget 2GB, usage NULL, first -1d, last -30s, ONLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'devE',0,1073741824,524288000,'1.0.0','10.0.0.5','QQT/1.0 (Android 34; Test)', @now - INTERVAL 1 DAY),
 (999999,'devE',1,1073741824,524288000,'1.0.0','10.0.0.5','QQT/1.0 (Android 34; Test)', @now - INTERVAL 1 DAY),
 (999999,'devE',0,2147483648,NULL,'1.2.0','10.0.0.5','QQT/1.2 (Android 34; Test)', @now - INTERVAL 30 SECOND);

-- devF: 10 facts, budget 500MB, usage 1GB, first -90d, last -4h, OFFLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'devF',0,104857600,52428800,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',1,104857600,52428800,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',0,104857600,52428800,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',1,209715200,104857600,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',0,209715200,104857600,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',1,209715200,104857600,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',0,524288000,524288000,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',1,524288000,524288000,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',0,524288000,1073741824,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 90 DAY),
 (999999,'devF',1,524288000,1073741824,'1.0.0','10.0.0.6','QQT/1.0 (Android 31; Test)', @now - INTERVAL 4 HOUR);

-- devG: 2 facts, budget NULL, usage 100MB, first -5d, last -20s, ONLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'devG',0,NULL,52428800,'1.0.0','10.0.0.7','QQT/1.0 (Android 34; Test)', @now - INTERVAL 5 DAY),
 (999999,'devG',1,NULL,104857600,'1.1.0','10.0.0.7','QQT/1.1 (Android 34; Test)', @now - INTERVAL 20 SECOND);

-- devH: 2 facts, budget 2GB, usage 2GB, first -3d, last -5h, OFFLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'devH',0,1073741824,1073741824,'1.0.0','10.0.0.8','QQT/1.0 (Android 34; Test)', @now - INTERVAL 3 DAY),
 (999999,'devH',1,2147483648,2147483648,'1.2.0','10.0.0.8','QQT/1.2 (Android 34; Test)', @now - INTERVAL 5 HOUR);

-- filler1..5: 1 fact each, budget 200MB*i, usage 100MB*i, last -(i+2)h, OFFLINE
INSERT INTO access_facts (song_id, device_id, cache_hit, allocated_storage, used_storage, app_version, ip_address, user_agent, accessed_at) VALUES
 (999999,'filler1',0,209715200,104857600,'1.0.0','10.0.1.1','QQT/1.0 (Android 30; Test)', @now - INTERVAL 3 HOUR),
 (999999,'filler2',0,419430400,209715200,'1.0.0','10.0.1.2','QQT/1.0 (Android 30; Test)', @now - INTERVAL 4 HOUR),
 (999999,'filler3',0,629145600,314572800,'1.0.0','10.0.1.3','QQT/1.0 (Android 30; Test)', @now - INTERVAL 5 HOUR),
 (999999,'filler4',0,838860800,419430400,'1.0.0','10.0.1.4','QQT/1.0 (Android 30; Test)', @now - INTERVAL 6 HOUR),
 (999999,'filler5',0,1048576000,524288000,'1.0.0','10.0.1.5','QQT/1.0 (Android 30; Test)', @now - INTERVAL 7 HOUR);