CREATE DOMAIN public.dom6 AS text COLLATE pg_catalog."ru_RU"
	CONSTRAINT dom6_check CHECK ((VALUE <> ''::text));

ALTER DOMAIN public.dom6 OWNER TO botov_av;