process.env.JWT_SECRET = 'isolated-test-secret-not-used-outside-tests-123';
process.env.BUSINESS_TIME_ZONE = 'America/Lima';
require('./stock-sales.test.cjs');
require('./hardening.test.cjs');
require('./uploads-backups.test.cjs');
