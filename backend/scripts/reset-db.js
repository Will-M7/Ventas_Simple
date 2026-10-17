require('dotenv/config');
const { assertDestructiveAllowed } = require('../dist/utils/operationGuard');
assertDestructiveAllowed(process.env); // Always before opening the database.
const { password, text } = require('../dist/utils/validation');
password(process.env.BOOTSTRAP_ADMIN_PASSWORD, 12);
text(process.env.BOOTSTRAP_ADMIN_USERNAME, 'BOOTSTRAP_ADMIN_USERNAME', 80);
async function main() {
  const config = require('../dist/config/database');
  const { migrate } = require('../dist/config/migrations');
  await config.initDatabase();
  config.db.transaction(() => {
    config.db.exec('DROP TABLE IF EXISTS inventory_movements; DROP TABLE IF EXISTS sale_items; DROP TABLE IF EXISTS sales; DROP TABLE IF EXISTS products; DROP TABLE IF EXISTS categories; DROP TABLE IF EXISTS settings; DROP TABLE IF EXISTS users; DROP TABLE IF EXISTS schema_migrations;');
    migrate(config.db);
  }).immediate();
  await config.bootstrap(config.db);
  config.db.close();
  console.log('Base de desarrollo recreada.');
}
main().catch(() => { console.error('Reset falló'); process.exitCode = 1; });
