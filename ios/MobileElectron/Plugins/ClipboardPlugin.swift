//
//  ClipboardPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import UIKit

public class ClipboardPlugin: IBridgePlugin {
    public let moduleName = "clipboard"

    public init() {}

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_0_PUBLIC
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (_ code: Int, _ data: [String: Any]?, _ message: String) -> Void
    ) {
        DispatchQueue.main.async {
            if action.caseInsensitiveCompare("readText") == .orderedSame {
                let text = UIPasteboard.general.string ?? ""
                completion(200, ["text": text], "success")
                return
            }

            if action.caseInsensitiveCompare("writeText") == .orderedSame {
                let text = params["text"] as? String ?? ""
                UIPasteboard.general.string = text
                completion(200, ["success": true], "success")
                return
            }

            if action.caseInsensitiveCompare("hasText") == .orderedSame {
                let hasText = UIPasteboard.general.hasStrings
                completion(200, ["hasText": hasText], "success")
                return
            }

            if action.caseInsensitiveCompare("clear") == .orderedSame {
                UIPasteboard.general.strings = []
                completion(200, ["success": true], "success")
                return
            }

            completion(404, nil, "Unknown clipboard action: \(action)")
        }
    }
}
