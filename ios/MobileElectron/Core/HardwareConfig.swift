//
//  HardwareConfig.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import WebKit

public struct HardwareConfig: Codable {
    public var cpuCores: Int?
    public var deviceMemory: Int?
    public var glVendor: String?
    public var glRenderer: String?
    public var platform: String?
    public var model: String?
    public var maxTouchPoints: Int?
    public var screenWidth: Int?
    public var screenHeight: Int?
    public var devicePixelRatio: Double?
    public var colorDepth: Int?
    public var networkType: String?
    public var batteryLevel: Double?
    public var batteryCharging: Bool?
    public var audioNoiseEnabled: Bool?

    public init(
        cpuCores: Int? = 8,
        deviceMemory: Int? = 8,
        glVendor: String? = "Apple Inc.",
        glRenderer: String? = "Apple GPU",
        platform: String? = "iPhone",
        model: String? = "iPhone 15 Pro",
        maxTouchPoints: Int? = 5,
        screenWidth: Int? = 393,
        screenHeight: Int? = 852,
        devicePixelRatio: Double? = 3.0,
        colorDepth: Int? = 24,
        networkType: String? = "5g",
        batteryLevel: Double? = 0.90,
        batteryCharging: Bool? = false,
        audioNoiseEnabled: Bool? = true
    ) {
        self.cpuCores = cpuCores
        self.deviceMemory = deviceMemory
        self.glVendor = glVendor
        self.glRenderer = glRenderer
        self.platform = platform
        self.model = model
        self.maxTouchPoints = maxTouchPoints
        self.screenWidth = screenWidth
        self.screenHeight = screenHeight
        self.devicePixelRatio = devicePixelRatio
        self.colorDepth = colorDepth
        self.networkType = networkType
        self.batteryLevel = batteryLevel
        self.batteryCharging = batteryCharging
        self.audioNoiseEnabled = audioNoiseEnabled
    }

    public static func fromPreset(_ preset: String) -> HardwareConfig {
        switch preset.lowercased() {
        case "desktop":
            return HardwareConfig(
                cpuCores: 16,
                deviceMemory: 32,
                glVendor: "Apple Inc.",
                glRenderer: "Apple M3 Max",
                platform: "MacIntel",
                model: "MacBook Pro",
                maxTouchPoints: 0,
                screenWidth: 1920,
                screenHeight: 1080,
                devicePixelRatio: 2.0,
                colorDepth: 24,
                networkType: "4g",
                batteryLevel: 1.0,
                batteryCharging: true,
                audioNoiseEnabled: false
            )
        case "budget":
            return HardwareConfig(
                cpuCores: 4,
                deviceMemory: 4,
                glVendor: "Apple Inc.",
                glRenderer: "Apple A13 GPU",
                platform: "iPhone",
                model: "iPhone 11",
                maxTouchPoints: 5,
                screenWidth: 375,
                screenHeight: 667,
                devicePixelRatio: 2.0,
                colorDepth: 24,
                networkType: "4g",
                batteryLevel: 0.55,
                batteryCharging: true,
                audioNoiseEnabled: true
            )
        default: // flagship
            return HardwareConfig(
                cpuCores: 8,
                deviceMemory: 16,
                glVendor: "Apple Inc.",
                glRenderer: "Apple A17 Pro GPU",
                platform: "iPhone",
                model: "iPhone 15 Pro",
                maxTouchPoints: 5,
                screenWidth: 393,
                screenHeight: 852,
                devicePixelRatio: 3.0,
                colorDepth: 24,
                networkType: "5g",
                batteryLevel: 0.88,
                batteryCharging: false,
                audioNoiseEnabled: true
            )
        }
    }

    public func generateStealthScript() -> String {
        let cores = cpuCores ?? 8
        let memory = deviceMemory ?? 8
        let vendor = glVendor ?? "Apple Inc."
        let renderer = glRenderer ?? "Apple GPU"
        let plat = platform ?? "iPhone"
        let touch = maxTouchPoints ?? 5
        let width = screenWidth ?? 393
        let height = screenHeight ?? 852
        let dpr = devicePixelRatio ?? 3.0
        let depth = colorDepth ?? 24
        let netType = networkType ?? "5g"
        let batLevel = batteryLevel ?? 0.88
        let batCharging = (batteryCharging ?? false) ? "true" : "false"
        let audioNoise = (audioNoiseEnabled ?? true) ? "true" : "false"

        return """
        (function() {
            'use strict';
            if (window.__ME_STEALTH_APPLIED__) return;
            window.__ME_STEALTH_APPLIED__ = true;

            const makeNativeString = (fnName) => `function ${fnName}() { [native code] }`;
            const nativeToString = Function.prototype.toString;
            const customToStrings = new WeakMap();

            function defineGetter(proto, prop, value) {
                if (!proto) return;
                try {
                    const getter = function() { return value; };
                    Object.defineProperty(getter, 'name', { value: `get ${prop}`, configurable: true });
                    customToStrings.set(getter, makeNativeString(`get ${prop}`));
                    Object.defineProperty(proto, prop, {
                        get: getter,
                        set: undefined,
                        enumerable: true,
                        configurable: true
                    });
                } catch(e) {}
            }

            // 1. CPU & 内存计算硬件
            defineGetter(Navigator.prototype, 'hardwareConcurrency', \(cores));
            defineGetter(Navigator.prototype, 'deviceMemory', \(memory));
            defineGetter(Navigator.prototype, 'platform', '\(plat)');
            defineGetter(Navigator.prototype, 'maxTouchPoints', \(touch));

            // 2. 屏幕硬件参数
            defineGetter(Screen.prototype, 'width', \(width));
            defineGetter(Screen.prototype, 'height', \(height));
            defineGetter(Screen.prototype, 'availWidth', \(width));
            defineGetter(Screen.prototype, 'availHeight', \(height));
            defineGetter(Screen.prototype, 'colorDepth', \(depth));
            defineGetter(Screen.prototype, 'pixelDepth', \(depth));
            defineGetter(window, 'devicePixelRatio', \(dpr));

            // 3. WebGL GPU 显卡硬件深度 Hook
            function hookWebGL(glContext) {
                if (!glContext || !glContext.prototype) return;
                const origGetParameter = glContext.prototype.getParameter;
                const hookedGetParameter = function(param) {
                    if (param === 0x9245) return '\(vendor)';
                    if (param === 0x9246) return '\(renderer)';
                    if (param === 0x1F00) return '\(vendor)';
                    if (param === 0x1F01) return '\(renderer)';
                    return origGetParameter.apply(this, arguments);
                };
                Object.defineProperty(hookedGetParameter, 'name', { value: 'getParameter', configurable: true });
                customToStrings.set(hookedGetParameter, makeNativeString('getParameter'));
                glContext.prototype.getParameter = hookedGetParameter;

                const origGetExtension = glContext.prototype.getExtension;
                const hookedGetExtension = function(name) {
                    if (name === 'WEBGL_debug_renderer_info') {
                        return { UNMASKED_VENDOR_WEBGL: 0x9245, UNMASKED_RENDERER_WEBGL: 0x9246 };
                    }
                    return origGetExtension.apply(this, arguments);
                };
                Object.defineProperty(hookedGetExtension, 'name', { value: 'getExtension', configurable: true });
                customToStrings.set(hookedGetExtension, makeNativeString('getExtension'));
                glContext.prototype.getExtension = hookedGetExtension;
            }
            if (typeof WebGLRenderingContext !== 'undefined') hookWebGL(WebGLRenderingContext);
            if (typeof WebGL2RenderingContext !== 'undefined') hookWebGL(WebGL2RenderingContext);

            // 4. 电池状态 (navigator.getBattery)
            if (typeof navigator.getBattery === 'function') {
                try {
                    const fakeBattery = {
                        charging: \(batCharging),
                        chargingTime: 3600,
                        dischargingTime: Infinity,
                        level: \(batLevel),
                        onchargingchange: null,
                        onlevelchange: null,
                        addEventListener: function() {},
                        removeEventListener: function() {}
                    };
                    const hookedGetBattery = function() { return Promise.resolve(fakeBattery); };
                    Object.defineProperty(hookedGetBattery, 'name', { value: 'getBattery', configurable: true });
                    customToStrings.set(hookedGetBattery, makeNativeString('getBattery'));
                    navigator.getBattery = hookedGetBattery;
                } catch(e) {}
            }

            // 5. 声卡硬件指纹防检测微扰
            if (\(audioNoise) && typeof AudioBuffer !== 'undefined') {
                try {
                    const origGetChannelData = AudioBuffer.prototype.getChannelData;
                    AudioBuffer.prototype.getChannelData = function(channel) {
                        const array = origGetChannelData.call(this, channel);
                        for (let i = 0; i < array.length; i += 100) {
                            array[i] += 0.0000001;
                        }
                        return array;
                    };
                    Object.defineProperty(AudioBuffer.prototype.getChannelData, 'name', { value: 'getChannelData', configurable: true });
                    customToStrings.set(AudioBuffer.prototype.getChannelData, makeNativeString('getChannelData'));
                } catch(e) {}
            }

            // 6. Function.prototype.toString 递归防检测保护
            try {
                const toStringProxy = function() {
                    if (customToStrings.has(this)) return customToStrings.get(this);
                    return nativeToString.apply(this, arguments);
                };
                Object.defineProperty(toStringProxy, 'name', { value: 'toString', configurable: true });
                customToStrings.set(toStringProxy, makeNativeString('toString'));
                Function.prototype.toString = toStringProxy;
            } catch(e) {}
        })();
        """
    }

    public func toDictionary() -> [String: Any] {
        return [
            "cpuCores": cpuCores ?? 8,
            "deviceMemory": deviceMemory ?? 8,
            "glVendor": glVendor ?? "Apple Inc.",
            "glRenderer": glRenderer ?? "Apple GPU",
            "platform": platform ?? "iPhone",
            "model": model ?? "iPhone 15 Pro",
            "maxTouchPoints": maxTouchPoints ?? 5,
            "screenWidth": screenWidth ?? 393,
            "screenHeight": screenHeight ?? 852,
            "devicePixelRatio": devicePixelRatio ?? 3.0,
            "colorDepth": colorDepth ?? 24,
            "networkType": networkType ?? "5g",
            "batteryLevel": batteryLevel ?? 0.88,
            "batteryCharging": batteryCharging ?? false,
            "audioNoiseEnabled": audioNoiseEnabled ?? true
        ]
    }
}
