//
//  HapticsPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import UIKit

public class HapticsPlugin: IBridgePlugin {
    public let moduleName = "haptics"

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
            if action.caseInsensitiveCompare("impact") == .orderedSame {
                let styleStr = (params["style"] as? String)?.lowercased() ?? "medium"
                let feedbackStyle: UIImpactFeedbackGenerator.FeedbackStyle
                switch styleStr {
                case "light":
                    feedbackStyle = .light
                case "heavy":
                    feedbackStyle = .heavy
                case "medium":
                default:
                    feedbackStyle = .medium
                }
                let generator = UIImpactFeedbackGenerator(style: feedbackStyle)
                generator.prepare()
                generator.impactOccurred()
                completion(200, ["success": true], "success")
                return
            }

            if action.caseInsensitiveCompare("notification") == .orderedSame {
                let typeStr = (params["type"] as? String)?.lowercased() ?? "success"
                let feedbackType: UINotificationFeedbackGenerator.FeedbackType
                switch typeStr {
                case "warning":
                    feedbackType = .warning
                case "error":
                    feedbackType = .error
                case "success":
                default:
                    feedbackType = .success
                }
                let generator = UINotificationFeedbackGenerator()
                generator.prepare()
                generator.notificationOccurred(feedbackType)
                completion(200, ["success": true], "success")
                return
            }

            if action.caseInsensitiveCompare("selection") == .orderedSame {
                let generator = UISelectionFeedbackGenerator()
                generator.prepare()
                generator.selectionChanged()
                completion(200, ["success": true], "success")
                return
            }

            if action.caseInsensitiveCompare("vibrate") == .orderedSame {
                let generator = UINotificationFeedbackGenerator()
                generator.prepare()
                generator.notificationOccurred(.warning)
                completion(200, ["success": true], "success")
                return
            }

            completion(404, nil, "HapticsPlugin: 未知动作 \(action)")
        }
    }
}
