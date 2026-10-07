package com.example.cookiebrowser.core;

import org.json.JSONObject;

/**
 * 商业级硬件参数与设备指纹虚拟化全维度模型 (Full-Spectrum Hardware Fingerprint & Stealth Engine)
 */
public class HardwareConfig {

    // 1. CPU & 内存计算硬件
    public int cpuCores = 8;                         // navigator.hardwareConcurrency (4, 8, 12, 16, 24, 32)
    public int deviceMemory = 8;                     // navigator.deviceMemory (GB: 4, 8, 12, 16, 32)

    // 2. 触控与系统平台
    public int maxTouchPoints = 5;                   // navigator.maxTouchPoints (移动端 5 或 10, 桌面 0)
    public String platform = "Linux aarch64";        // navigator.platform
    public String model = "SM-S9280";                // 真实设备型号 (如 Galaxy S24 Ultra / Xiaomi 14)
    public String architecture = "arm64";            // CPU 架构
    public int bitness = 64;                         // 系统位数

    // 3. WebGL GPU 显卡硬件 (UNMASKED_VENDOR & RENDERER)
    public String glVendor = "Qualcomm";             // WebGL UNMASKED_VENDOR_WEBGL
    public String glRenderer = "Adreno (TM) 750";    // WebGL UNMASKED_RENDERER_WEBGL (骁龙8 Gen3 旗舰级 GPU)

    // 4. 显示屏幕与视口硬件
    public int screenWidth = 1080;                   // screen.width
    public int screenHeight = 2400;                  // screen.height
    public float devicePixelRatio = 3.0f;            // window.devicePixelRatio (DPR)
    public int colorDepth = 24;                      // screen.colorDepth & pixelDepth

    // 5. 网卡与连接信息
    public String networkType = "4g";                // navigator.connection.effectiveType
    public double networkDownlink = 10.0;            // downlink (Mbps)
    public int networkRtt = 50;                      // rtt (ms)

    // 6. 电池硬件参数
    public boolean batteryCharging = true;           // battery.charging
    public double batteryLevel = 0.92;               // battery.level (0.0 ~ 1.0)

    // 7. 音频硬件指纹微扰 (Web Audio API Anti-Fingerprinting)
    public boolean audioNoiseEnabled = true;         // 注入微弱硬件声卡浮点微扰，阻断声卡指纹跨域追踪

    public HardwareConfig() {
    }

    /**
     * 预设 1: 顶级旗舰机 (骁龙 8 Gen3 / 16GB / 2K 屏)
     */
    public static HardwareConfig createFlagship() {
        HardwareConfig config = new HardwareConfig();
        config.cpuCores = 8;
        config.deviceMemory = 16;
        config.maxTouchPoints = 10;
        config.platform = "Linux aarch64";
        config.model = "SM-S9280";
        config.architecture = "arm64";
        config.bitness = 64;
        config.glVendor = "Qualcomm";
        config.glRenderer = "Adreno (TM) 750";
        config.screenWidth = 1080;
        config.screenHeight = 2400;
        config.devicePixelRatio = 3.0f;
        config.colorDepth = 24;
        config.networkType = "5g";
        config.networkDownlink = 20.0;
        config.networkRtt = 30;
        config.batteryCharging = false;
        config.batteryLevel = 0.88;
        config.audioNoiseEnabled = true;
        return config;
    }

    /**
     * 预设 2: 主流中端机 (天玑 8300 / 8GB / 1080P)
     */
    public static HardwareConfig createMidRange() {
        HardwareConfig config = new HardwareConfig();
        config.cpuCores = 8;
        config.deviceMemory = 8;
        config.maxTouchPoints = 5;
        config.platform = "Linux aarch64";
        config.model = "2311DRK48C";
        config.architecture = "arm64";
        config.bitness = 64;
        config.glVendor = "ARM";
        config.glRenderer = "Mali-G615 MC6";
        config.screenWidth = 1080;
        config.screenHeight = 2400;
        config.devicePixelRatio = 2.75f;
        config.colorDepth = 24;
        config.networkType = "4g";
        config.networkDownlink = 10.0;
        config.networkRtt = 60;
        config.batteryCharging = true;
        config.batteryLevel = 0.65;
        config.audioNoiseEnabled = true;
        return config;
    }

    /**
     * 预设 3: 入门千元机 (高通骁龙 695 / 4GB)
     */
    public static HardwareConfig createBudget() {
        HardwareConfig config = new HardwareConfig();
        config.cpuCores = 4;
        config.deviceMemory = 4;
        config.maxTouchPoints = 5;
        config.platform = "Linux armv8l";
        config.model = "CPH2343";
        config.architecture = "arm";
        config.bitness = 32;
        config.glVendor = "Qualcomm";
        config.glRenderer = "Adreno (TM) 619";
        config.screenWidth = 720;
        config.screenHeight = 1600;
        config.devicePixelRatio = 2.0f;
        config.colorDepth = 24;
        config.networkType = "4g";
        config.networkDownlink = 5.0;
        config.networkRtt = 100;
        config.batteryCharging = false;
        config.batteryLevel = 0.42;
        config.audioNoiseEnabled = true;
        return config;
    }

    /**
     * 预设 4: 桌面级工作站 (RTX 4080 / 16核 / 32GB)
     */
    public static HardwareConfig createDesktop() {
        HardwareConfig config = new HardwareConfig();
        config.cpuCores = 16;
        config.deviceMemory = 32;
        config.maxTouchPoints = 0;
        config.platform = "Win32";
        config.model = "PC-Desktop";
        config.architecture = "x86_64";
        config.bitness = 64;
        config.glVendor = "Google Inc. (NVIDIA)";
        config.glRenderer = "ANGLE (NVIDIA, NVIDIA GeForce RTX 4080 Direct3D11 vs_5_0 ps_5_0, D3D11)";
        config.screenWidth = 1920;
        config.screenHeight = 1080;
        config.devicePixelRatio = 1.0f;
        config.colorDepth = 24;
        config.networkType = "4g";
        config.networkDownlink = 50.0;
        config.networkRtt = 20;
        config.batteryCharging = true;
        config.batteryLevel = 1.0;
        config.audioNoiseEnabled = false;
        return config;
    }

    public static HardwareConfig fromPresetOrParams(String preset, JSONObject params) {
        HardwareConfig config;
        if ("flagship".equalsIgnoreCase(preset)) {
            config = createFlagship();
        } else if ("midrange".equalsIgnoreCase(preset)) {
            config = createMidRange();
        } else if ("budget".equalsIgnoreCase(preset)) {
            config = createBudget();
        } else if ("desktop".equalsIgnoreCase(preset)) {
            config = createDesktop();
        } else {
            config = createFlagship();
        }

        if (params != null) {
            if (params.has("cpuCores")) config.cpuCores = params.optInt("cpuCores", config.cpuCores);
            if (params.has("hardwareConcurrency")) config.cpuCores = params.optInt("hardwareConcurrency", config.cpuCores);
            if (params.has("deviceMemory")) config.deviceMemory = params.optInt("deviceMemory", config.deviceMemory);
            if (params.has("memory")) config.deviceMemory = params.optInt("memory", config.deviceMemory);
            if (params.has("maxTouchPoints")) config.maxTouchPoints = params.optInt("maxTouchPoints", config.maxTouchPoints);
            if (params.has("platform")) config.platform = params.optString("platform", config.platform);
            if (params.has("model")) config.model = params.optString("model", config.model);
            if (params.has("glVendor")) config.glVendor = params.optString("glVendor", config.glVendor);
            if (params.has("glRenderer")) config.glRenderer = params.optString("glRenderer", config.glRenderer);
            if (params.has("screenWidth")) config.screenWidth = params.optInt("screenWidth", config.screenWidth);
            if (params.has("screenHeight")) config.screenHeight = params.optInt("screenHeight", config.screenHeight);
            if (params.has("devicePixelRatio")) config.devicePixelRatio = (float) params.optDouble("devicePixelRatio", config.devicePixelRatio);
            if (params.has("colorDepth")) config.colorDepth = params.optInt("colorDepth", config.colorDepth);
            if (params.has("networkType")) config.networkType = params.optString("networkType", config.networkType);
            if (params.has("batteryLevel")) config.batteryLevel = params.optDouble("batteryLevel", config.batteryLevel);
            if (params.has("batteryCharging")) config.batteryCharging = params.optBoolean("batteryCharging", config.batteryCharging);
            if (params.has("audioNoiseEnabled")) config.audioNoiseEnabled = params.optBoolean("audioNoiseEnabled", config.audioNoiseEnabled);
        }

        return config;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("cpuCores", cpuCores);
            json.put("hardwareConcurrency", cpuCores);
            json.put("deviceMemory", deviceMemory);
            json.put("maxTouchPoints", maxTouchPoints);
            json.put("platform", platform);
            json.put("model", model);
            json.put("architecture", architecture);
            json.put("bitness", bitness);
            json.put("glVendor", glVendor);
            json.put("glRenderer", glRenderer);
            json.put("screenWidth", screenWidth);
            json.put("screenHeight", screenHeight);
            json.put("devicePixelRatio", devicePixelRatio);
            json.put("colorDepth", colorDepth);
            json.put("networkType", networkType);
            json.put("batteryLevel", batteryLevel);
            json.put("batteryCharging", batteryCharging);
            json.put("audioNoiseEnabled", audioNoiseEnabled);
        } catch (Exception ignored) {}
        return json;
    }

    /**
     * 生成商业级全维度防检测 (Stealth) 硬件指纹注入 JavaScript 脚本
     * 覆盖：CPU、内存、Client Hints (userAgentData)、WebGL 1/2 GPU、屏幕尺寸与颜色深度、音频声卡微扰、电池、网卡
     * 所有 Getter 均重写原型链并伪装成 [native code]，完全保护原型链与 Function.prototype.toString
     */
    public String generateInjectionScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");
        sb.append("  'use strict';\n");
        sb.append("  if (window.__ME_STEALTH_APPLIED__) return;\n");
        sb.append("  window.__ME_STEALTH_APPLIED__ = true;\n\n");

        sb.append("  const HW = {\n");
        sb.append("    cores: ").append(cpuCores).append(",\n");
        sb.append("    memory: ").append(deviceMemory).append(",\n");
        sb.append("    maxTouchPoints: ").append(maxTouchPoints).append(",\n");
        sb.append("    platform: ").append(escapeJsString(platform)).append(",\n");
        sb.append("    model: ").append(escapeJsString(model)).append(",\n");
        sb.append("    architecture: ").append(escapeJsString(architecture)).append(",\n");
        sb.append("    bitness: ").append(bitness).append(",\n");
        sb.append("    glVendor: ").append(escapeJsString(glVendor)).append(",\n");
        sb.append("    glRenderer: ").append(escapeJsString(glRenderer)).append(",\n");
        sb.append("    width: ").append(screenWidth).append(",\n");
        sb.append("    height: ").append(screenHeight).append(",\n");
        sb.append("    dpr: ").append(devicePixelRatio).append(",\n");
        sb.append("    colorDepth: ").append(colorDepth).append(",\n");
        sb.append("    networkType: ").append(escapeJsString(networkType)).append(",\n");
        sb.append("    downlink: ").append(networkDownlink).append(",\n");
        sb.append("    rtt: ").append(networkRtt).append(",\n");
        sb.append("    batteryCharging: ").append(batteryCharging).append(",\n");
        sb.append("    batteryLevel: ").append(batteryLevel).append(",\n");
        sb.append("    audioNoise: ").append(audioNoiseEnabled).append("\n");
        sb.append("  };\n\n");

        sb.append("  const makeNativeString = (fnName) => `function ${fnName}() { [native code] }`;\n");
        sb.append("  const nativeToString = Function.prototype.toString;\n");
        sb.append("  const customToStrings = new WeakMap();\n\n");

        sb.append("  function defineGetter(proto, prop, value) {\n");
        sb.append("    if (!proto) return;\n");
        sb.append("    try {\n");
        sb.append("      const getter = function() { return value; };\n");
        sb.append("      Object.defineProperty(getter, 'name', { value: `get ${prop}`, configurable: true });\n");
        sb.append("      customToStrings.set(getter, makeNativeString(`get ${prop}`));\n");
        sb.append("      Object.defineProperty(proto, prop, {\n");
        sb.append("        get: getter,\n");
        sb.append("        set: undefined,\n");
        sb.append("        enumerable: true,\n");
        sb.append("        configurable: true\n");
        sb.append("      });\n");
        sb.append("    } catch(e) {}\n");
        sb.append("  }\n\n");

        // 1. CPU & 内存计算硬件
        sb.append("  if (HW.cores) defineGetter(Navigator.prototype, 'hardwareConcurrency', HW.cores);\n");
        sb.append("  if (HW.memory) defineGetter(Navigator.prototype, 'deviceMemory', HW.memory);\n");
        sb.append("  if (HW.maxTouchPoints !== undefined) defineGetter(Navigator.prototype, 'maxTouchPoints', HW.maxTouchPoints);\n");
        sb.append("  if (HW.platform) defineGetter(Navigator.prototype, 'platform', HW.platform);\n\n");

        // 2. Client Hints 高阶硬件信息 (navigator.userAgentData)
        sb.append("  if (navigator.userAgentData) {\n");
        sb.append("    try {\n");
        sb.append("      const origGetHighEntropy = navigator.userAgentData.getHighEntropyValues;\n");
        sb.append("      const hookedGetHighEntropy = function(hints) {\n");
        sb.append("        return origGetHighEntropy.call(this, hints).then(res => {\n");
        sb.append("          if (hints.includes('model')) res.model = HW.model;\n");
        sb.append("          if (hints.includes('architecture')) res.architecture = HW.architecture;\n");
        sb.append("          if (hints.includes('bitness')) res.bitness = String(HW.bitness);\n");
        sb.append("          return res;\n");
        sb.append("        });\n");
        sb.append("      };\n");
        sb.append("      Object.defineProperty(hookedGetHighEntropy, 'name', { value: 'getHighEntropyValues', configurable: true });\n");
        sb.append("      customToStrings.set(hookedGetHighEntropy, makeNativeString('getHighEntropyValues'));\n");
        sb.append("      navigator.userAgentData.getHighEntropyValues = hookedGetHighEntropy;\n");
        sb.append("    } catch(e) {}\n");
        sb.append("  }\n\n");

        // 3. 屏幕与显示硬件
        sb.append("  if (HW.width > 0) {\n");
        sb.append("    defineGetter(Screen.prototype, 'width', HW.width);\n");
        sb.append("    defineGetter(Screen.prototype, 'availWidth', HW.width);\n");
        sb.append("  }\n");
        sb.append("  if (HW.height > 0) {\n");
        sb.append("    defineGetter(Screen.prototype, 'height', HW.height);\n");
        sb.append("    defineGetter(Screen.prototype, 'availHeight', HW.height);\n");
        sb.append("  }\n");
        sb.append("  if (HW.dpr > 0) {\n");
        sb.append("    defineGetter(window, 'devicePixelRatio', HW.dpr);\n");
        sb.append("  }\n");
        sb.append("  if (HW.colorDepth > 0) {\n");
        sb.append("    defineGetter(Screen.prototype, 'colorDepth', HW.colorDepth);\n");
        sb.append("    defineGetter(Screen.prototype, 'pixelDepth', HW.colorDepth);\n");
        sb.append("  }\n\n");

        // 4. WebGL 1.0 & WebGL 2.0 显卡硬件深度 Hook
        sb.append("  function hookWebGL(glContext) {\n");
        sb.append("    if (!glContext || !glContext.prototype) return;\n");
        sb.append("    const origGetParameter = glContext.prototype.getParameter;\n");
        sb.append("    const hookedGetParameter = function(param) {\n");
        sb.append("      // 0x9245 = UNMASKED_VENDOR_WEBGL\n");
        sb.append("      if (param === 0x9245) return HW.glVendor;\n");
        sb.append("      // 0x9246 = UNMASKED_RENDERER_WEBGL\n");
        sb.append("      if (param === 0x9246) return HW.glRenderer;\n");
        sb.append("      // 0x1F00 = VENDOR\n");
        sb.append("      if (param === 0x1F00) return HW.glVendor;\n");
        sb.append("      // 0x1F01 = RENDERER\n");
        sb.append("      if (param === 0x1F01) return HW.glRenderer;\n");
        sb.append("      return origGetParameter.apply(this, arguments);\n");
        sb.append("    };\n");
        sb.append("    Object.defineProperty(hookedGetParameter, 'name', { value: 'getParameter', configurable: true });\n");
        sb.append("    customToStrings.set(hookedGetParameter, makeNativeString('getParameter'));\n");
        sb.append("    glContext.prototype.getParameter = hookedGetParameter;\n\n");
        sb.append("    // 保证 getExtension('WEBGL_debug_renderer_info') 永远可用\n");
        sb.append("    const origGetExtension = glContext.prototype.getExtension;\n");
        sb.append("    const hookedGetExtension = function(name) {\n");
        sb.append("      if (name === 'WEBGL_debug_renderer_info') {\n");
        sb.append("        return { UNMASKED_VENDOR_WEBGL: 0x9245, UNMASKED_RENDERER_WEBGL: 0x9246 };\n");
        sb.append("      }\n");
        sb.append("      return origGetExtension.apply(this, arguments);\n");
        sb.append("    };\n");
        sb.append("    Object.defineProperty(hookedGetExtension, 'name', { value: 'getExtension', configurable: true });\n");
        sb.append("    customToStrings.set(hookedGetExtension, makeNativeString('getExtension'));\n");
        sb.append("    glContext.prototype.getExtension = hookedGetExtension;\n");
        sb.append("  }\n\n");
        sb.append("  if (typeof WebGLRenderingContext !== 'undefined') hookWebGL(WebGLRenderingContext);\n");
        sb.append("  if (typeof WebGL2RenderingContext !== 'undefined') hookWebGL(WebGL2RenderingContext);\n\n");

        // 5. 电池硬件参数 (navigator.getBattery)
        sb.append("  if (typeof navigator.getBattery === 'function') {\n");
        sb.append("    try {\n");
        sb.append("      const fakeBattery = {\n");
        sb.append("        charging: HW.batteryCharging,\n");
        sb.append("        chargingTime: HW.batteryCharging ? 3600 : Infinity,\n");
        sb.append("        dischargingTime: HW.batteryCharging ? Infinity : 18000,\n");
        sb.append("        level: HW.batteryLevel,\n");
        sb.append("        onchargingchange: null,\n");
        sb.append("        onlevelchange: null,\n");
        sb.append("        addEventListener: function() {},\n");
        sb.append("        removeEventListener: function() {}\n");
        sb.append("      };\n");
        sb.append("      const hookedGetBattery = function() { return Promise.resolve(fakeBattery); };\n");
        sb.append("      Object.defineProperty(hookedGetBattery, 'name', { value: 'getBattery', configurable: true });\n");
        sb.append("      customToStrings.set(hookedGetBattery, makeNativeString('getBattery'));\n");
        sb.append("      navigator.getBattery = hookedGetBattery;\n");
        sb.append("    } catch(e) {}\n");
        sb.append("  }\n\n");

        // 6. 网络硬件连接 (navigator.connection)
        sb.append("  if (navigator.connection) {\n");
        sb.append("    try {\n");
        sb.append("      defineGetter(navigator.connection, 'effectiveType', HW.networkType);\n");
        sb.append("      defineGetter(navigator.connection, 'downlink', HW.downlink);\n");
        sb.append("      defineGetter(navigator.connection, 'rtt', HW.rtt);\n");
        sb.append("    } catch(e) {}\n");
        sb.append("  }\n\n");

        // 7. 声卡硬件指纹防检测微扰 (Web Audio API)
        sb.append("  if (HW.audioNoise && typeof AudioBuffer !== 'undefined') {\n");
        sb.append("    try {\n");
        sb.append("      const origGetChannelData = AudioBuffer.prototype.getChannelData;\n");
        sb.append("      AudioBuffer.prototype.getChannelData = function(channel) {\n");
        sb.append("        const array = origGetChannelData.call(this, channel);\n");
        sb.append("        // 注入极微小且确定性的浮点微扰，破坏声卡硬件跨域唯一哈希指纹\n");
        sb.append("        for (let i = 0; i < array.length; i += 100) {\n");
        sb.append("          array[i] += 0.0000001;\n");
        sb.append("        }\n");
        sb.append("        return array;\n");
        sb.append("      };\n");
        sb.append("      Object.defineProperty(AudioBuffer.prototype.getChannelData, 'name', { value: 'getChannelData', configurable: true });\n");
        sb.append("      customToStrings.set(AudioBuffer.prototype.getChannelData, makeNativeString('getChannelData'));\n");
        sb.append("    } catch(e) {}\n");
        sb.append("  }\n\n");

        // 8. 终极保护：Function.prototype.toString 递归防检测
        sb.append("  try {\n");
        sb.append("    const toStringProxy = function() {\n");
        sb.append("      if (customToStrings.has(this)) return customToStrings.get(this);\n");
        sb.append("      return nativeToString.apply(this, arguments);\n");
        sb.append("    };\n");
        sb.append("    Object.defineProperty(toStringProxy, 'name', { value: 'toString', configurable: true });\n");
        sb.append("    customToStrings.set(toStringProxy, makeNativeString('toString'));\n");
        sb.append("    Function.prototype.toString = toStringProxy;\n");
        sb.append("  } catch(e) {}\n");

        sb.append("})();\n");
        return sb.toString();
    }

    private static String escapeJsString(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
