//
//  HotUpdateManager.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import CryptoKit
import Compression

public final class HotUpdateManager {
    public static let shared = HotUpdateManager()

    private let fileManager = FileManager.default
    private let baseDir: URL
    private let activeDir: URL
    private let stagingDir: URL
    private let activeVersionFile: URL

    private var activeVersionInfo: [String: Any]?

    private init() {
        let appSupport = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let bundlesDir = appSupport.appendingPathComponent("MobileElectron/bundles", isDirectory: true)
        self.baseDir = bundlesDir
        self.activeDir = bundlesDir.appendingPathComponent("active", isDirectory: true)
        self.stagingDir = bundlesDir.appendingPathComponent("staging", isDirectory: true)
        self.activeVersionFile = bundlesDir.appendingPathComponent("active_version.json")

        try? fileManager.createDirectory(at: bundlesDir, withIntermediateDirectories: true)
        verifyAndLoadActiveVersion()
    }

    public func initialize() {
        verifyAndLoadActiveVersion()
    }

    private func verifyAndLoadActiveVersion() {
        if fileManager.fileExists(atPath: activeVersionFile.path) && fileManager.fileExists(atPath: activeDir.path) {
            let indexHtml = activeDir.appendingPathComponent("index.html")
            if fileManager.fileExists(atPath: indexHtml.path) {
                if let data = try? Data(contentsOf: activeVersionFile),
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    self.activeVersionInfo = json
                    NSLog("[HotUpdateManager-iOS] Loaded active sandbox bundle version: %@", (json["version"] as? String) ?? "unknown")
                    return
                }
            }
            // 文件损坏或缺失 index.html，执行自愈回滚
            NSLog("[HotUpdateManager-iOS] Sandbox bundle corrupted, rolling back to builtin")
            _ = rollback()
        } else {
            self.activeVersionInfo = nil
        }
    }

    public func hasActiveBundle() -> Bool {
        guard fileManager.fileExists(atPath: activeDir.path) else { return false }
        let indexHtml = activeDir.appendingPathComponent("index.html")
        return fileManager.fileExists(atPath: indexHtml.path) && activeVersionInfo != nil
    }

    public func getActiveBundleDir() -> URL? {
        return hasActiveBundle() ? activeDir : nil
    }

    public func getActiveVersionInfo() -> [String: Any] {
        if hasActiveBundle(), var info = activeVersionInfo {
            info["isHotUpdated"] = true
            info["source"] = "sandbox"
            return info
        } else {
            let ver = AppConfigManager.shared.config.app.version
            return [
                "version": ver,
                "buildNumber": 100,
                "isHotUpdated": false,
                "source": "builtin",
                "description": "原生底座内置静态离线包"
            ]
        }
    }

    /**
     * 检查远端是否有新版本离线包
     */
    public func checkForUpdate(customUrl: String?, completion: @escaping (Result<[String: Any], Error>) -> Void) {
        let checkUrlStr = (customUrl?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
            ?? "http://127.0.0.1:8088/update-manifest.json"

        guard let url = URL(string: checkUrlStr) else {
            completion(.failure(NSError(domain: "HotUpdate", code: 400, userInfo: [NSLocalizedDescriptionKey: "无效的更新地址"])))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 8.0

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                completion(.failure(NSError(domain: "HotUpdate", code: 500, userInfo: [NSLocalizedDescriptionKey: "无法解析更新清单 JSON"])))
                return
            }

            let latestVersion = (json["latestVersion"] as? String) ?? ""
            let latestBuild = (json["buildNumber"] as? Int) ?? 0
            var packageUrl = (json["packageUrl"] as? String) ?? ""
            let packageHash = (json["packageHash"] as? String) ?? ""
            let packageSize = (json["packageSize"] as? Int64) ?? 0
            let desc = (json["description"] as? String) ?? ""
            let mandatory = (json["mandatory"] as? Bool) ?? false

            // 解析相对 packageUrl
            if !packageUrl.hasPrefix("http://") && !packageUrl.hasPrefix("https://") {
                if let absUrl = URL(string: packageUrl, relativeTo: url) {
                    packageUrl = absUrl.absoluteString
                }
            }

            let curInfo = self.getActiveVersionInfo()
            let curVersion = (curInfo["version"] as? String) ?? "2.0.0"
            let curBuild = (curInfo["buildNumber"] as? Int) ?? 100

            let hasUpdate: Bool
            if latestBuild > 0 && curBuild > 0 {
                hasUpdate = latestBuild > curBuild
            } else {
                hasUpdate = self.compareVersions(v1: latestVersion, v2: curVersion) > 0
            }

            let result: [String: Any] = [
                "hasUpdate": hasUpdate,
                "currentVersion": curVersion,
                "currentBuild": curBuild,
                "latestVersion": latestVersion,
                "latestBuild": latestBuild,
                "packageUrl": packageUrl,
                "packageHash": packageHash,
                "packageSize": packageSize,
                "description": desc,
                "mandatory": mandatory
            ]
            completion(.success(result))
        }.resume()
    }

    /**
     * 下载离线包并解压安装到沙箱
     */
    public func downloadAndInstall(
        packageUrl: String,
        expectedHash: String,
        progress: ((Int, Int64, Int64) -> Void)? = nil,
        completion: @escaping (Result<[String: Any], Error>) -> Void
    ) {
        guard let url = URL(string: packageUrl) else {
            completion(.failure(NSError(domain: "HotUpdate", code: 400, userInfo: [NSLocalizedDescriptionKey: "无效的 packageUrl"])))
            return
        }

        try? fileManager.removeItem(at: stagingDir)
        try? fileManager.createDirectory(at: stagingDir, withIntermediateDirectories: true)

        let downloadFile = stagingDir.appendingPathComponent("update_bundle.mep")

        var request = URLRequest(url: url)
        request.timeoutInterval = 30.0

        let task = URLSession.shared.downloadTask(with: request) { [weak self] tempUrl, response, error in
            guard let self = self else { return }
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let tempUrl = tempUrl else {
                completion(.failure(NSError(domain: "HotUpdate", code: 500, userInfo: [NSLocalizedDescriptionKey: "下载文件不存在"])))
                return
            }

            do {
                try? self.fileManager.removeItem(at: downloadFile)
                try self.fileManager.moveItem(at: tempUrl, to: downloadFile)

                // 1. 校验 SHA-256
                let fileData = try Data(contentsOf: downloadFile)
                let hash = SHA256.hash(data: fileData)
                let actualHashHex = hash.compactMap { String(format: "%02x", $0) }.joined()
                let actualHash = "sha256:" + actualHashHex

                if !expectedHash.isEmpty {
                    var cleanExpected = expectedHash.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                    if cleanExpected.hasPrefix("sha256:") {
                        cleanExpected = String(cleanExpected.dropFirst(7))
                    }
                    if actualHashHex.lowercased() != cleanExpected {
                        throw NSError(domain: "HotUpdate", code: 403, userInfo: [
                            NSLocalizedDescriptionKey: "离线包完整性校验失败 (Hash 不匹配)! 预期: \(cleanExpected), 实际: \(actualHashHex)"
                        ])
                    }
                    NSLog("[HotUpdateManager-iOS] SHA-256 integrity verified successfully")
                }

                // 2. 解压到 staging/unzipped
                let unzippedDir = self.stagingDir.appendingPathComponent("unzipped", isDirectory: true)
                try? self.fileManager.createDirectory(at: unzippedDir, withIntermediateDirectories: true)

                try self.unzip(archiveData: fileData, destination: unzippedDir)

                var finalContentDir = unzippedDir
                let candidateIndex = unzippedDir.appendingPathComponent("index.html")
                if !self.fileManager.fileExists(atPath: candidateIndex.path) {
                    let distSub = unzippedDir.appendingPathComponent("dist", isDirectory: true)
                    if self.fileManager.fileExists(atPath: distSub.appendingPathComponent("index.html").path) {
                        finalContentDir = distSub
                    } else {
                        throw NSError(domain: "HotUpdate", code: 422, userInfo: [
                            NSLocalizedDescriptionKey: "离线包解压内容损坏，未找到主入口 index.html"
                        ])
                    }
                }

                // 尝试读取 manifest.json
                var internalManifest: [String: Any]?
                let manifestFile = finalContentDir.appendingPathComponent("manifest.json")
                if let mData = try? Data(contentsOf: manifestFile),
                   let mJson = try? JSONSerialization.jsonObject(with: mData) as? [String: Any] {
                    internalManifest = mJson
                }

                // 3. 原子切换沙箱 active 目录
                let backupDir = self.baseDir.appendingPathComponent("active_backup", isDirectory: true)
                try? self.fileManager.removeItem(at: backupDir)

                if self.fileManager.fileExists(atPath: self.activeDir.path) {
                    try? self.fileManager.moveItem(at: self.activeDir, to: backupDir)
                }

                try self.fileManager.createDirectory(at: self.activeDir, withIntermediateDirectories: true)
                let items = try self.fileManager.contentsOfDirectory(at: finalContentDir, includingPropertiesForKeys: nil)
                for item in items {
                    let dest = self.activeDir.appendingPathComponent(item.lastPathComponent)
                    try self.fileManager.copyItem(at: item, to: dest)
                }

                // 4. 写入 active_version.json
                var newVersionInfo: [String: Any] = [:]
                if let internalManifest = internalManifest {
                    newVersionInfo["version"] = internalManifest["version"] ?? "2.1.0"
                    newVersionInfo["buildNumber"] = internalManifest["buildNumber"] ?? 210
                    newVersionInfo["description"] = internalManifest["description"] ?? ""
                } else {
                    newVersionInfo["version"] = "2.1.0"
                    newVersionInfo["buildNumber"] = 210
                    newVersionInfo["description"] = "热更新完成"
                }
                newVersionInfo["hash"] = actualHash
                newVersionInfo["appliedAt"] = Date().timeIntervalSince1970
                newVersionInfo["isHotUpdated"] = true
                newVersionInfo["source"] = "sandbox"

                let vData = try JSONSerialization.data(withJSONObject: newVersionInfo, options: [.prettyPrinted])
                try vData.write(to: self.activeVersionFile)
                self.activeVersionInfo = newVersionInfo

                // 清理临时文件
                try? self.fileManager.removeItem(at: self.stagingDir)
                try? self.fileManager.removeItem(at: backupDir)

                NSLog("[HotUpdateManager-iOS] Hot update deployed successfully: %@", (newVersionInfo["version"] as? String) ?? "")
                completion(.success(newVersionInfo))

            } catch {
                try? self.fileManager.removeItem(at: self.stagingDir)
                completion(.failure(error))
            }
        }

        task.resume()
    }

    public func rollback() -> Bool {
        try? fileManager.removeItem(at: activeDir)
        try? fileManager.removeItem(at: activeVersionFile)
        try? fileManager.removeItem(at: stagingDir)
        self.activeVersionInfo = nil
        NSLog("[HotUpdateManager-iOS] Rolled back to builtin bundle")
        return true
    }

    // MARK: - Lightweight Pure-Swift Zip Extractor
    private func unzip(archiveData: Data, destination: URL) throws {
        var offset = 0
        let count = archiveData.count

        while offset + 30 <= count {
            // Check Local File Header Signature: 0x04034b50
            let sig = archiveData.subdata(in: offset..<offset+4).withUnsafeBytes { $0.load(as: UInt32.self) }
            if sig != 0x04034b50 {
                // Reached central directory or end of archive
                break
            }

            let method = archiveData.subdata(in: offset+8..<offset+10).withUnsafeBytes { $0.load(as: UInt16.self) }
            let compSize = Int(archiveData.subdata(in: offset+18..<offset+22).withUnsafeBytes { $0.load(as: UInt32.self) })
            let uncompSize = Int(archiveData.subdata(in: offset+22..<offset+26).withUnsafeBytes { $0.load(as: UInt32.self) })
            let nameLen = Int(archiveData.subdata(in: offset+26..<offset+28).withUnsafeBytes { $0.load(as: UInt16.self) })
            let extraLen = Int(archiveData.subdata(in: offset+28..<offset+30).withUnsafeBytes { $0.load(as: UInt16.self) })

            offset += 30
            guard offset + nameLen <= count else { break }

            let nameData = archiveData.subdata(in: offset..<offset+nameLen)
            guard let fileName = String(data: nameData, encoding: .utf8) else {
                offset += nameLen + extraLen + compSize
                continue
            }
            offset += nameLen + extraLen

            guard offset + compSize <= count else { break }
            let fileData = archiveData.subdata(in: offset..<offset+compSize)
            offset += compSize

            let targetUrl = destination.appendingPathComponent(fileName)

            // 防止路径穿越
            guard targetUrl.standardized.path.hasPrefix(destination.standardized.path) else {
                throw NSError(domain: "HotUpdate", code: 400, userInfo: [NSLocalizedDescriptionKey: "Zip 路径穿越异常: \(fileName)"])
            }

            if fileName.hasSuffix("/") {
                try? fileManager.createDirectory(at: targetUrl, withIntermediateDirectories: true)
            } else {
                try? fileManager.createDirectory(at: targetUrl.deletingLastPathComponent(), withIntermediateDirectories: true)

                let decompressedData: Data?
                if method == 0 {
                    decompressedData = fileData
                } else if method == 8 {
                    decompressedData = decompressDeflate(compressedData: fileData, uncompressedSize: uncompSize)
                } else {
                    decompressedData = nil
                }

                if let output = decompressedData {
                    try output.write(to: targetUrl)
                }
            }
        }
    }

    private func decompressDeflate(compressedData: Data, uncompressedSize: Int) -> Data? {
        guard uncompressedSize > 0 else { return Data() }
        var destBuffer = [UInt8](repeating: 0, count: uncompressedSize)
        let decompressedSize = compressedData.withUnsafeBytes { rawBufferPointer -> Int in
            guard let srcPointer = rawBufferPointer.baseAddress?.assumingMemoryBound(to: UInt8.self) else { return 0 }
            return compression_decode_buffer(
                &destBuffer,
                uncompressedSize,
                srcPointer,
                compressedData.count,
                nil,
                COMPRESSION_ZLIB
            )
        }
        guard decompressedSize > 0 else { return nil }
        return Data(bytes: destBuffer, count: decompressedSize)
    }

    private func compareVersions(v1: String, v2: String) -> Int {
        let p1 = v1.components(separatedBy: ".").compactMap { Int($0.filter { "0"..."9" ~= $0 }) }
        let p2 = v2.components(separatedBy: ".").compactMap { Int($0.filter { "0"..."9" ~= $0 }) }
        let maxCount = max(p1.count, p2.count)
        for i in 0..<maxCount {
            let n1 = i < p1.count ? p1[i] : 0
            let n2 = i < p2.count ? p2[i] : 0
            if n1 > n2 { return 1 }
            if n1 < n2 { return -1 }
        }
        return 0
    }
}
