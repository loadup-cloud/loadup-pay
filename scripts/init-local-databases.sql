-- Local PostgreSQL setup; run with psql as admin, outside a transaction.
-- Each database uses its default public schema.
-- Service migrations, including Outbox, are applied by Flyway at application startup.
\set ON_ERROR_STOP on

SELECT 'CREATE DATABASE loadup_payment OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_payment')
\gexec
SELECT 'CREATE DATABASE loadup_acquire OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_acquire')
\gexec
SELECT 'CREATE DATABASE loadup_merchantcenter OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_merchantcenter')
\gexec
SELECT 'CREATE DATABASE loadup_limitcenter OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_limitcenter')
\gexec
SELECT 'CREATE DATABASE loadup_product OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_product')
\gexec
SELECT 'CREATE DATABASE loadup_realtime OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_realtime')
\gexec
SELECT 'CREATE DATABASE loadup_reconciliation OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_reconciliation')
\gexec

SELECT 'CREATE DATABASE loadup_notificationcenter OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_notificationcenter')
\gexec

SELECT 'CREATE DATABASE loadup_authserver OWNER admin'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'loadup_authserver')
\gexec
