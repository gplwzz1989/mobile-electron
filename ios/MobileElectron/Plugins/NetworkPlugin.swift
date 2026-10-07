//
//  NetworkPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

public class NetworkPlugin: IBridgePlugin {
    public let moduleName = "network"

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_2_CORE
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
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
}
