// @mobile-electron/cli - Create Command (Scaffolder Wrapper)

const path = require('path');
const { spawn } = require('child_process');
const logger = require('../utils/logger');

function createCommand(rawArgs = []) {
  const root = process.cwd();
  const scaffolderCli = path.join(root, 'packages', 'create-mobile-electron-app', 'bin', 'cli.js');

  const child = spawn('node', [scaffolderCli, ...rawArgs], {
    cwd: root,
    stdio: 'inherit'
  });

  child.on('close', (code) => {
    process.exit(code || 0);
  });
}

module.exports = createCommand;
