//
//  AppConfig.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

public struct WindowConfig: Codable {
    public var defaultUrl: String
    public var title: String
    public var fullscreen: Bool
    public var immersiveStatusBar: Bool
    public var statusBarColor: String
    public var statusBarDarkIcons: Bool
    public var showNativeDebugToolbar: Bool

    public init(
        defaultUrl: String = "file:///dist/index.html",
        title: String = "Mobile Electron Application",
        fullscreen: Bool = false,
        immersiveStatusBar: Bool = true,
        statusBarColor: String = "#FFFFFF",
        statusBarDarkIcons: Bool = true,
        showNativeDebugToolbar: Bool = false
    ) {
        self.defaultUrl = defaultUrl
        self.title = title
        self.fullscreen = fullscreen
        self.immersiveStatusBar = immersiveStatusBar
        self.statusBarColor = statusBarColor
        self.statusBarDarkIcons = statusBarDarkIcons
        self.showNativeDebugToolbar = showNativeDebugToolbar
    }
}

public struct SecurityConfig: Codable {
    public var whitelist: [String]
    public var allowInsecureContent: Bool

    public init(whitelist: [String] = [], allowInsecureContent: Bool = true) {
        self.whitelist = whitelist
        self.allowInsecureContent = allowInsecureContent
    }
}

public struct ProfileConfig: Codable {
    public var defaultProfile: String
    public var hardwarePreset: String

    public init(defaultProfile: String = "default", hardwarePreset: String = "flagship") {
        self.defaultProfile = defaultProfile
        self.hardwarePreset = hardwarePreset
    }
}

public struct AppMetaConfig: Codable {
    public var name: String
    public var appId: String
    public var version: String

    public init(name: String = "Mobile Electron", appId: String = "com.example.mobileelectron", version: String = "2.0.0") {
        self.name = name
        self.appId = appId
        self.version = version
    }
}

public struct AppConfig: Codable {
    public var app: AppMetaConfig
    public var window: WindowConfig
    public var security: SecurityConfig
    public var profile: ProfileConfig

    public init() {
        self.app = AppMetaConfig()
        self.window = WindowConfig()
        self.security = SecurityConfig()
        self.profile = ProfileConfig()
    }
}
