//
//  NetworkPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import Network

public class NetworkPlugin: IBridgePlugin {
    public let moduleName = "network"

    public weak var bridgeEngine: JsBridgeEngine?
    private let monitor = NWPathMonitor()
    private let monitorQueue = DispatchQueue(label: "com.mobileelectron.network.monitor")
    private var currentStatus: [String: Any] = [
        "isConnected": true,
        "type": "wifi",
        "isMetered": false
    ]

    public init() {
        startMonitoring()
    }

    private func startMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self = self else { return }
            let isConnected = (path.status == .satisfied)
            var netType = "none"
            if isConnected {
                if path.usesInterfaceType(.wifi) {
                    netType = "wifi"
                } else if path.usesInterfaceType(.cellular) {
                    netType = "cellular"
                } else if path.usesInterfaceType(.wiredEthernet) {
                    netType = "ethernet"
                } else {
                    netType = "other"
                }
            }
            let isMetered = path.isExpensive

            let status: [String: Any] = [
                "isConnected": isConnected,
                "type": netType,
                "isMetered": isMetered
            ]
            self.currentStatus = status

            self.bridgeEngine?.sendEvent(eventName: "network.onNetworkChange", data: status)
        }
        monitor.start(queue: monitorQueue)
    }

    public func getRequiredTier(action: String) -> Int {
        if action == "getStatus" {
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
        if action == "getStatus" {
            completion(200, currentStatus, "success")
            return
        }

        if action == "fetch" {
            guard let urlStr = params["url"] as? String, let url = URL(string: urlStr) else {
                completion(400, nil, "Invalid URL")
                return
            }

            var request = URLRequest(url: url)
            request.httpMethod = (params["method"] as? String ?? "GET").uppercased()

            if let headers = params["headers"] as? [String: String] {
                for (k, v) in headers {
                    request.setValue(v, forHTTPHeaderField: k)
                }
            }

            if let bodyStr = params["body"] as? String {
                request.httpBody = bodyStr.data(using: .utf8)
            }

            let task = URLSession.shared.dataTask(with: request) { data, response, error in
                if let error = error {
                    completion(500, nil, "Network error: \(error.localizedDescription)")
                    return
                }

                let httpResponse = response as? HTTPURLResponse
                let status = httpResponse?.statusCode ?? 200
                var resHeaders = [String: String]()
                if let allHeaders = httpResponse?.allHeaderFields {
                    for (k, v) in allHeaders {
                        resHeaders["\(k)"] = "\(v)"
                    }
                }

                let body = data != nil ? String(data: data!, encoding: .utf8) ?? "" : ""
                completion(200, [
                    "status": status,
                    "headers": resHeaders,
                    "body": body
                ], "success")
            }
            task.resume()
        } else {
            completion(404, nil, "Unknown action '\(action)' in module 'network'")
        }
    }

    deinit {
        monitor.cancel()
    }
}
