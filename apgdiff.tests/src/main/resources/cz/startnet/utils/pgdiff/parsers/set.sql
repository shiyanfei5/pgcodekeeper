SET enable_seqscan TO off;
SET enable_bitmapscan TO off;
SET enable_sort TO off;
RESET enable_seqscan;
RESET enable_bitmapscan;
RESET enable_sort;
SET enable_sort = false;
SET enable_indexscan TO off;
RESET enable_indexscan;
SET search_path TO temp_func_test, public;
set enable_hashagg = true;
set enable_indexscan = false;
set work_mem = '384kB';
SET max_stack_depth = '100kB';
RESET max_stack_depth;
SET LOCAL TIME ZONE 10.5;
SET LOCAL TIME ZONE -8;
SET client_min_messages TO 'warning';
SET parallel_setup_cost = 0;
SET parallel_tuple_cost = 0;
SET min_parallel_table_scan_size = 0;
SET max_parallel_workers_per_gather = 4;
SET enable_indexonlyscan = off;
set local max_parallel_workers_per_gather = 0;
SET SESSION AUTHORIZATION regress_unpriv_user;
SET SESSION AUTHORIZATION 'regress_publication_user';
RESET SESSION AUTHORIZATION;
SET enable_seqscan = OFF;
SET enable_indexscan = ON;
SET enable_bitmapscan = OFF;
RESET enable_seqscan;
RESET enable_indexscan;
RESET enable_bitmapscan;
SET row_security TO ON;
SET ROLE regress_rls_bob;
SET password_encryption = 'novalue';
SET password_encryption = true;
SET password_encryption = 'md5';
SET password_encryption = 'scram-sha-256';
set plpgsql.variable_conflict = error;
set plpgsql.extra_warnings to 'shadowed_variables';
set plpgsql.extra_errors to 'too_many_rows';
set plpgsql.check_asserts = off;
set plpgsql.print_strict_params to true;
reset plpgsql.extra_errors;
reset plpgsql.extra_warnings;
reset plpgsql.check_asserts;
reset plpgsql.print_strict_params;
SET DateStyle = 'Postgres, MDY';
RESET TIME ZONE;
reset enable_parallel_append;
SET xmlbinary TO base64;
SET XML OPTION DOCUMENT;
SET vacuum_cost_delay TO 40;
