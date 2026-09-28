SELECT 'CREATE DATABASE aibe7_test'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aibe7_test') \gexec
