// @mobile-electron/cli - Console Logger Utility

const colors = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  italic: '\x1b[3m',
  underline: '\x1b[4m',
  
  black: '\x1b[30m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
  cyan: '\x1b[36m',
  white: '\x1b[37m',
  
  bgBlue: '\x1b[44m',
  bgCyan: '\x1b[46m',
  bgGreen: '\x1b[42m',
  bgRed: '\x1b[41m'
};

const logger = {
  colors,

  banner() {
    console.log(`
${colors.cyan}${colors.bold}  __  __       _     _ _        ______ _           _                   
 |  \\/  |     | |   (_) |      |  ____| |         | |                  
 | \\  / | ___ | |__  _| | ___  | |__  | | ___  ___| |_ _ __ ___  _ __  
 | |\\/| |/ _ \\| '_ \\| | |/ _ \\ |  __| | |/ _ \\/ __| __| '__/ _ \\| '_ \\ 
 | |  | | (_) | |_) | | |  __/ | |____| |  __/ (__| |_| | | (_) | | | |
 |_|  |_|\\___/|_.__/|_|_|\\___| |______|_|\\___|\\___|\\__|_|  \\___/|_| |_|${colors.reset}
 ${colors.dim}v2.0.0 · Industrial-grade Cross-Platform Hybrid Framework for Android & iOS${colors.reset}
`);
  },

  info(msg) {
    console.log(`${colors.blue}ℹ${colors.reset}  ${msg}`);
  },

  success(msg) {
    console.log(`${colors.green}✔${colors.reset}  ${colors.bold}${msg}${colors.reset}`);
  },

  warn(msg) {
    console.log(`${colors.yellow}⚠${colors.reset}  ${colors.yellow}${msg}${colors.reset}`);
  },

  error(msg) {
    console.log(`${colors.red}✖${colors.reset}  ${colors.red}${colors.bold}${msg}${colors.reset}`);
  },

  step(current, total, msg) {
    console.log(`\n${colors.cyan}[${current}/${total}]${colors.reset} ${colors.bold}${msg}${colors.reset}`);
  },

  box(title, lines) {
    const maxLen = Math.max(title.length + 4, ...lines.map(l => l.replace(/\x1b\[\d+m/g, '').length + 4), 50);
    const border = '─'.repeat(maxLen);
    console.log(`\n${colors.cyan}┌${border}┐${colors.reset}`);
    console.log(`${colors.cyan}│${colors.reset} ${colors.bold}${title.padEnd(maxLen - 2)}${colors.reset} ${colors.cyan}│${colors.reset}`);
    console.log(`${colors.cyan}├${border}┤${colors.reset}`);
    lines.forEach(line => {
      const plain = line.replace(/\x1b\[\d+m/g, '');
      const pad = maxLen - 2 - plain.length;
      console.log(`${colors.cyan}│${colors.reset} ${line}${' '.repeat(Math.max(0, pad))} ${colors.cyan}│${colors.reset}`);
    });
    console.log(`${colors.cyan}└${border}┘${colors.reset}\n`);
  }
};

module.exports = logger;
