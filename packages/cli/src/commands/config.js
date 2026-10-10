// @mobile-electron/cli - Config Command

const fs = require('fs');
const path = require('path');
const logger = require('../utils/logger');

function configCommand() {
  logger.banner();
  const root = process.cwd();
  const androidCfgPath = path.join(root, 'app', 'src', 'main', 'assets', 'app-config.json');
  const iosCfgPath = path.join(root, 'ios', 'MobileElectron', 'app-config.json');

  console.log(`${logger.colors.bold}⚙️  Mobile Electron 双端原生配置中心自检:${logger.colors.reset}\n`);

  function readConfig(p, name) {
    if (!fs.existsSync(p)) {
      console.log(`  ✖ [${name}] 配置文件不存在: ${p}`);
      return null;
    }
    try {
      const content = fs.readFileSync(p, 'utf8');
      const json = JSON.parse(content);
      const appName = json.app?.name || json.appName || 'Mobile Electron';
      const defaultUrl = json.window?.defaultUrl || json.defaultUrl || '(未设置)';
      const isLocal = defaultUrl.startsWith('file:///');
      const whitelist = json.security?.whitelist || json.domainWhitelist || [];
      const hwPreset = json.profile?.hardwarePreset || (json.hardwareEmulation?.enabled ? 'enabled' : 'off');
      const updateUrl = json.hotUpdate?.manifestUrl || json.updateManifestUrl || '未配置';

      console.log(`  ✔ [${name}] 配置解析正常 (${p}):`);
      console.log(`    • 应用名称:        ${appName}`);
      console.log(`    • 默认加载 URL:    ${defaultUrl}`);
      console.log(`    • 离线沙箱模式:    ${isLocal ? '已激活 (Local Mode: file:///)' : '在线模式 (Online Mode: http/https)'}`);
      console.log(`    • 域名安全白名单:  ${whitelist.length} 条规则`);
      if (whitelist.length > 0) {
        console.log(`      (${whitelist.slice(0, 5).join(', ')}${whitelist.length > 5 ? ' ...' : ''})`);
      }
      console.log(`    • 硬件指纹预设:    ${hwPreset}`);
      console.log(`    • 热更清单地址:    ${updateUrl}`);
      return { appName, defaultUrl, isLocal, whitelist, hwPreset };
    } catch (err) {
      console.log(`  ✖ [${name}] 解析失败: ${err.message}`);
      return null;
    }
  }

  const aCfg = readConfig(androidCfgPath, 'Android');
  console.log('');
  const iCfg = readConfig(iosCfgPath, 'iOS');

  console.log('\n' + '='.repeat(60));
  if (aCfg && iCfg) {
    const urlsMatch = aCfg.defaultUrl === iCfg.defaultUrl;
    if (urlsMatch) {
      logger.success('Android 与 iOS 核心配置一致对齐！');
    } else {
      logger.warn(`双端 defaultUrl 不一致: Android=${aCfg.defaultUrl}, iOS=${iCfg.defaultUrl}`);
    }
  }
}

module.exports = configCommand;
