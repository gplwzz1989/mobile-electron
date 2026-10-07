//
//  FilePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

public class FilePlugin: IBridgePlugin {
    public let moduleName = "file"
    private let fileQueue = DispatchQueue(label: "com.mobileelectron.file.queue", attributes: .concurrent)

    public func getRequiredTier(action: String) -> Int {
        if action == "getPaths" || action == "exists" {
            return DomainWhitelistManager.TIER_1_BUSINESS
        }
        return DomainWhitelistManager.TIER_2_CORE
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        fileQueue.async {
            switch action {
            case "getPaths":
                let docs = self.getSafeBaseDir("documents")
                let cache = self.getSafeBaseDir("cache")
                let temp = self.getSafeBaseDir("temp")
                completion(200, [
                    "documents": docs.path,
                    "cache": cache.path,
                    "temp": temp.path
                ], "success")

            case "write":
                guard let relativePath = params["path"] as? String, !relativePath.isEmpty else {
                    completion(400, nil, "Missing path parameter")
                    return
                }
                let content = params["content"] as? String ?? ""
                let encoding = params["encoding"] as? String ?? "utf8"
                let append = params["append"] as? Bool ?? false
                let dir = params["directory"] as? String ?? "documents"

                let targetUrl = self.resolveSafeUrl(dir: dir, relativePath: relativePath)
                let parentDir = targetUrl.deletingLastPathComponent()

                do {
                    try FileManager.default.createDirectory(at: parentDir, withIntermediateDirectories: true)
                    let data: Data
                    if encoding.lowercased() == "base64" {
                        data = Data(base64Encoded: content) ?? Data()
                    } else {
                        data = content.data(using: .utf8) ?? Data()
                    }

                    if append && FileManager.default.fileExists(atPath: targetUrl.path) {
                        let handle = try FileHandle(forWritingTo: targetUrl)
                        handle.seekToEndOfFile()
                        handle.write(data)
                        handle.closeFile()
                    } else {
                        try data.write(to: targetUrl, options: .atomic)
                    }

                    completion(200, [
                        "success": true,
                        "bytesWritten": data.count,
                        "fullPath": targetUrl.path
                    ], "success")
                } catch {
                    completion(500, nil, "Write failed: \(error.localizedDescription)")
                }

            case "read":
                guard let relativePath = params["path"] as? String, !relativePath.isEmpty else {
                    completion(400, nil, "Missing path parameter")
                    return
                }
                let encoding = params["encoding"] as? String ?? "utf8"
                let dir = params["directory"] as? String ?? "documents"
                let targetUrl = self.resolveSafeUrl(dir: dir, relativePath: relativePath)

                guard FileManager.default.fileExists(atPath: targetUrl.path) else {
                    completion(404, nil, "File not found: \(relativePath)")
                    return
                }

                do {
                    let data = try Data(contentsOf: targetUrl)
                    let result: String
                    if encoding.lowercased() == "base64" {
                        result = data.base64EncodedString()
                    } else {
                        result = String(data: data, encoding: .utf8) ?? ""
                    }
                    completion(200, [
                        "content": result,
                        "size": data.count,
                        "encoding": encoding
                    ], "success")
                } catch {
                    completion(500, nil, "Read failed: \(error.localizedDescription)")
                }

            case "exists":
                guard let relativePath = params["path"] as? String, !relativePath.isEmpty else {
                    completion(400, nil, "Missing path parameter")
                    return
                }
                let dir = params["directory"] as? String ?? "documents"
                let targetUrl = self.resolveSafeUrl(dir: dir, relativePath: relativePath)
                var isDir: ObjCBool = false
                let exists = FileManager.default.fileExists(atPath: targetUrl.path, isDirectory: &isDir)

                completion(200, [
                    "exists": exists,
                    "isFile": exists && !isDir.boolValue,
                    "isDirectory": exists && isDir.boolValue
                ], "success")

            case "delete":
                guard let relativePath = params["path"] as? String, !relativePath.isEmpty else {
                    completion(400, nil, "Missing path parameter")
                    return
                }
                let dir = params["directory"] as? String ?? "documents"
                let targetUrl = self.resolveSafeUrl(dir: dir, relativePath: relativePath)

                do {
                    if FileManager.default.fileExists(atPath: targetUrl.path) {
                        try FileManager.default.removeItem(at: targetUrl)
                    }
                    completion(200, ["success": true], "success")
                } catch {
                    completion(500, nil, "Delete error: \(error.localizedDescription)")
                }

            case "list":
                guard let relativePath = params["path"] as? String else {
                    completion(400, nil, "Missing path parameter")
                    return
                }
                let dir = params["directory"] as? String ?? "documents"
                let targetUrl = self.resolveSafeUrl(dir: dir, relativePath: relativePath)

                do {
                    let contents = try FileManager.default.contentsOfDirectory(atPath: targetUrl.path)
                    let items = contents.map { name -> [String: Any] in
                        let fileUrl = targetUrl.appendingPathComponent(name)
                        var isDir: ObjCBool = false
                        FileManager.default.fileExists(atPath: fileUrl.path, isDirectory: &isDir)
                        let attrs = try? FileManager.default.attributesOfItem(atPath: fileUrl.path)
                        let size = (attrs?[.size] as? NSNumber)?.int64Value ?? 0
                        let modified = ((attrs?[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0) * 1000
                        return [
                            "name": name,
                            "isDirectory": isDir.boolValue,
                            "size": size,
                            "lastModified": Int64(modified)
                        ]
                    }
                    completion(200, ["files": items], "success")
                } catch {
                    completion(500, nil, "List failed: \(error.localizedDescription)")
                }

            case "mkdir":
                let relativePath = params["path"] as? String ?? ""
                let dir = params["directory"] as? String ?? "documents"
                let targetUrl = self.resolveSafeUrl(dir: dir, relativePath: relativePath)

                do {
                    try FileManager.default.createDirectory(at: targetUrl, withIntermediateDirectories: true)
                    completion(200, ["success": true], "success")
                } catch {
                    completion(500, nil, "Mkdir failed: \(error.localizedDescription)")
                }

            case "download":
                guard let urlStr = params["url"] as? String, let sourceUrl = URL(string: urlStr),
                      let destPath = params["destinationPath"] as? String, !destPath.isEmpty else {
                    completion(400, nil, "Missing url or destinationPath")
                    return
                }
                let dir = params["directory"] as? String ?? "documents"
                let targetUrl = self.resolveSafeUrl(dir: dir, relativePath: destPath)

                let task = URLSession.shared.downloadTask(with: sourceUrl) { localTempUrl, _, err in
                    if let err = err {
                        completion(500, nil, "Download failed: \(err.localizedDescription)")
                        return
                    }
                    guard let localTemp = localTempUrl else {
                        completion(500, nil, "Empty downloaded file")
                        return
                    }
                    do {
                        try? FileManager.default.removeItem(at: targetUrl)
                        try FileManager.default.createDirectory(at: targetUrl.deletingLastPathComponent(), withIntermediateDirectories: true)
                        try FileManager.default.moveItem(at: localTemp, to: targetUrl)
                        let attrs = try? FileManager.default.attributesOfItem(atPath: targetUrl.path)
                        let size = (attrs?[.size] as? NSNumber)?.int64Value ?? 0
                        completion(200, ["success": true, "size": size, "fullPath": targetUrl.path], "success")
                    } catch {
                        completion(500, nil, "File save error: \(error.localizedDescription)")
                    }
                }
                task.resume()

            default:
                completion(404, nil, "Unknown action '\(action)' in module 'file'")
            }
        }
    }

    private func getSafeBaseDir(_ type: String) -> URL {
        if type.lowercased() == "cache" {
            return FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        } else if type.lowercased() == "temp" {
            return URL(fileURLWithPath: NSTemporaryDirectory())
        }
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    private func resolveSafeUrl(dir: String, relativePath: String) -> URL {
        let base = getSafeBaseDir(dir)
        var sanitized = relativePath.replacingOccurrences(of: "..", with: "")
        if sanitized.hasPrefix("/") {
            sanitized = String(sanitized.dropFirst())
        }
        return base.appendingPathComponent(sanitized)
    }
}
