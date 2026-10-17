// Compatibility entry point: never targets BASE_URL or an existing server.
const { spawnSync } = require('node:child_process');
const result = spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run','test:unit'], {cwd: __dirname, stdio: 'inherit'});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
