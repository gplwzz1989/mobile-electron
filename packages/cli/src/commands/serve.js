// @mobile-electron/cli - Serve Command (Hot Update & Remote Config Server)

const path = require('path');
const { spawn } = require('child_process');
const logger = require('../utils/logger');

function serveCommand(args = {}) {
  const root = process.cwd();
  const port = args.port || '8088';
  process.env.PORT = port;

  const scriptPath = path.join(root, 'scripts', 'serve-update.js');
  const child = spawn('node', [scriptPath], {
    cwd: root,
    env: { ...process.env, PORT: port },
    stdio: 'inherit'
  });

  child.on('close', (code) => {
    process.exit(code || 0);
  });
}

module.exports = serveCommand;
