SET TIMEZONE TO 'UTC';

ALTER TABLE table1
	DROP CONSTRAINT chk_table1;

ALTER TABLE table1
	ADD CONSTRAINT chk_table1 CHECK ((1 > 1));
