package com.example.cookiebrowser.core;

import org.json.JSONObject;

/**
 * 商业级硬件参数与设备指纹虚拟化配置模型
 */
public class HardwareConfig {

    public int cpuCores = 8;                         // navigator.hardwareConcurrency (CPU 核心数，如 4, 8, 12, 16)
    public int deviceMemory = 8;                     // navigator.deviceMemory (内存大小 GB，如 2, 4, 8, 12, 16)
    public int maxTouchPoints = 5;                   // navigator.maxTouchPoints
    public String platform = "Linux aarch64";        // navigator.platform
    public String glVendor = "Qualcomm";             // WebGL UNMASKED_VENDOR_WEBGL
    public String glRenderer = "Adreno (TM) 740";    // WebGL UNMASKED_RENDERER_WEBGL
    public int screenWidth = 0;                      // 自定义屏幕宽度 (0 表示跟随物理屏幕)
    public int screenHeight = 0;                     // 自定义屏幕高度
    public float devicePixelRatio = 0f;              // 自定义 DPR (0 表示跟随物理屏幕)

    public HardwareConfig() {
    }

    public static HardwareConfig createFlagship() {
        HardwareConfig config = new HardwareConfig();
        config.cpuCores = 8;
        config.deviceMemory = 16;
        config.maxTouchPoints = 10;
        config.platform = "Linux aarch64";
        config.glVendor = "Qualcomm";
        config.glRenderer = "Adreno (TM) 740";
        return config;
    }

    public static HardwareConfig createMidRange() {
        HardwareConfig config = new HardwareConfig();
        config.cpuCores = 8;
        config.deviceMemory = 8;
        config.maxTouchPoints = 5;
        config.platform = "Linux aarch64";
        config.glVendor = "ARM";
        config.glRenderer = "Mali-G715-MC11";
        return config;
    }

    public static HardwareConfig createBudget() {
        HardwareConfig config = new HardwareConfig();
        config.cpuCores = 4;
        config.deviceMemory = 4;
        config.maxTouchPoints = 5;
        config.platform = "Linux armv8l";
        config.glVendor = "Qualcomm";
        config.glRenderer = "Adreno (TM) 619";
        return config;
    }

    public static HardwareConfig createDesktop() {
        HardwareConfig config = new HardwareConfig();
        config.cpuCores = 16;
        config.deviceMemory = 32;
        config.maxTouchPoints = 0;
        config.platform = "Win32";
        config.glVendor = "Google Inc. (NVIDIA)";
        config.glRenderer = "ANGLE (NVIDIA, NVIDIA GeForce RTX 4080 Direct3D11 vs_5_0 ps_5_0, D3D11)";
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
            config = new HardwareConfig();
        }

        if (params != null) {
            if (params.has("cpuCores")) config.cpuCores = params.optInt("cpuCores", config.cpuCores);
            if (params.has("hardwareConcurrency")) config.cpuCores = params.optInt("hardwareConcurrency", config.cpuCores);
            if (params.has("deviceMemory")) config.deviceMemory = params.optInt("deviceMemory", config.deviceMemory);
            if (params.has("memory")) config.deviceMemory = params.optInt("memory", config.deviceMemory);
            if (params.has("maxTouchPoints")) config.maxTouchPoints = params.optInt("maxTouchPoints", config.maxTouchPoints);
            if (params.has("platform")) config.platform = params.optString("platform", config.platform);
            if (params.has("glVendor")) config.glVendor = params.optString("glVendor", config.glVendor);
            if (params.has("glRenderer")) config.glRenderer = params.optString("glRenderer", config.glRenderer);
            if (params.has("screenWidth")) config.screenWidth = params.optInt("screenWidth", config.screenWidth);
            if (params.has("screenHeight")) config.screenHeight = params.optInt("screenHeight", config.screenHeight);
            if (params.has("devicePixelRatio")) config.devicePixelRatio = (float) params.optDouble("devicePixelRatio", config.devicePixelRatio);
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
            json.put("glVendor", glVendor);
            json.put("glRenderer", glRenderer);
            json.put("screenWidth", screenWidth);
            json.put("screenHeight", screenHeight);
            json.put("devicePixelRatio", devicePixelRatio);
        } catch (Exception ignored) {}
        return json;
    }

    /**
     * 生成商业级防检测 (Stealth) 硬件指纹注入 JavaScript 脚本
     * 该脚本在 DocumentStart 阶段注入，所有 Getter 均重写原型链并伪装成 [native code]，完全绕过指纹安全检测
     */
    public String generateInjectionScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");
        sb.append("  'use strict';\n");
        sb.append("  const HW = {\n");
        sb.append("    cores: ").append(cpuCores).append(",\n");
        sb.append("    memory: ").append(deviceMemory).append(",\n");
        sb.append("    maxTouchPoints: ").append(maxTouchPoints).append(",\n");
        sb.append("    platform: ").append(escapeJsString(platform)).append(",\n");
        sb.append("    glVendor: ").append(escapeJsString(glVendor)).append(",\n");
        sb.append("    glRenderer: ").append(escapeJsString(glRenderer)).append(",\n");
        sb.append("    width: ").append(screenWidth).append(",\n");
        sb.append("    height: ").append(screenHeight).append(",\n");
        sb.append("    dpr: ").append(devicePixelRatio).append("\n");
        sb.append("  };\n\n");

        sb.append("  const makeNativeString = (fnName) => `function ${fnName}() { [native code] }`;\n");
        sb.append("  const nativeToString = Function.prototype.toString;\n");
        sb.append("  const customToStrings = new WeakMap();\n\n");

        sb.append("  function defineGetter(proto, prop, value) {\n");
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

        // 1. CPU 与 硬件并发数
        sb.append("  if (HW.cores) defineGetter(Navigator.prototype, 'hardwareConcurrency', HW.cores);\n");
        // 2. 内存大小 (GB)
        sb.append("  if (HW.memory) defineGetter(Navigator.prototype, 'deviceMemory', HW.memory);\n");
        // 3. 触控点数
        sb.append("  if (HW.maxTouchPoints !== undefined) defineGetter(Navigator.prototype, 'maxTouchPoints', HW.maxTouchPoints);\n");
        // 4. 平台架构
        sb.append("  if (HW.platform) defineGetter(Navigator.prototype, 'platform', HW.platform);\n");

        // 5. 屏幕参数 (若指定)
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
        sb.append("  }\n\n");

        // 6. WebGL GPU 显卡厂商与渲染器伪造
        sb.append("  function hookWebGL(glContext) {\n");
        sb.append("    if (!glContext || !glContext.prototype) return;\n");
        sb.append("    const origGetParameter = glContext.prototype.getParameter;\n");
        sb.append("    const hookedGetParameter = function(param) {\n");
        sb.append("      // 0x9245 = UNMASKED_VENDOR_WEBGL\n");
        sb.append("      if (param === 0x9245) return HW.glVendor || 'Qualcomm';\n");
        sb.append("      // 0x9246 = UNMASKED_RENDERER_WEBGL\n");
        sb.append("      if (param === 0x9246) return HW.glRenderer || 'Adreno (TM) 740';\n");
        sb.append("      // 0x1F00 = VENDOR\n");
        sb.append("      if (param === 0x1F00 && HW.glVendor) return HW.glVendor;\n");
        sb.append("      // 0x1F01 = RENDERER\n");
        sb.append("      if (param === 0x1F01 && HW.glRenderer) return HW.glRenderer;\n");
        sb.append("      return origGetParameter.apply(this, arguments);\n");
        sb.append("    };\n");
        sb.append("    Object.defineProperty(hookedGetParameter, 'name', { value: 'getParameter', configurable: true });\n");
        sb.append("    customToStrings.set(hookedGetParameter, makeNativeString('getParameter'));\n");
        sb.append("    glContext.prototype.getParameter = hookedGetParameter;\n");
        sb.append("  }\n\n");
        sb.append("  if (typeof WebGLRenderingContext !== 'undefined') hookWebGL(WebGLRenderingContext);\n");
        sb.append("  if (typeof WebGL2RenderingContext !== 'undefined') hookWebGL(WebGL2RenderingContext);\n\n");

        // 7. 防检测伪装：保护 Function.prototype.toString
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
