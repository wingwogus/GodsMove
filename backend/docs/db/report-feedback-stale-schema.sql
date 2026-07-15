alter table report_feedback add column if not exists source_fingerprint varchar(64);
alter table report_feedback drop constraint if exists report_feedback_status_check;
alter table report_feedback drop constraint if exists ck_report_feedback_status;
alter table report_feedback add constraint report_feedback_status_check
  check (status in ('PENDING', 'READY', 'FAILED', 'STALE'));
