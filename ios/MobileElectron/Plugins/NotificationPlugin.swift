//
//  NotificationPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import UserNotifications

public class NotificationPlugin: IBridgePlugin {
    public let moduleName = "notification"

    public init() {}

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_1_BUSINESS
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (_ code: Int, _ data: [String: Any]?, _ message: String) -> Void
    ) {
        let center = UNUserNotificationCenter.current()

        if action.caseInsensitiveCompare("show") == .orderedSame {
            center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
                guard granted else {
                    completion(403, ["success": false, "reason": "Notification permission not granted"], "Permission denied")
                    return
                }

                let content = UNMutableNotificationContent()
                let title = params["title"] as? String ?? (Bundle.main.infoDictionary?["CFBundleDisplayName"] as? String ?? "Mobile Electron")
                let body = params["body"] as? String ?? (params["message"] as? String ?? "")
                content.title = title
                content.body = body
                content.sound = .default

                if let badge = params["badge"] as? NSNumber {
                    content.badge = badge
                }

                if let data = params["data"] {
                    content.userInfo = ["notification_data": data]
                }

                let idStr: String
                if let idNum = params["id"] as? NSNumber {
                    idStr = "\(idNum)"
                } else if let id = params["id"] as? String {
                    idStr = id
                } else {
                    idStr = "notif_\(Int(Date().timeIntervalSince1970 * 1000))"
                }

                let request = UNNotificationRequest(identifier: idStr, content: content, trigger: nil)
                center.add(request) { addError in
                    if let addError = addError {
                        completion(500, nil, addError.localizedDescription)
                    } else {
                        completion(200, ["success": true, "id": idStr], "success")
                    }
                }
            }
            return
        }

        if action.caseInsensitiveCompare("cancel") == .orderedSame {
            let idStr: String?
            if let idNum = params["id"] as? NSNumber {
                idStr = "\(idNum)"
            } else {
                idStr = params["id"] as? String
            }
            if let id = idStr {
                center.removePendingNotificationRequests(withIdentifiers: [id])
                center.removeDeliveredNotifications(withIdentifiers: [id])
            }
            completion(200, ["success": true], "success")
            return
        }

        if action.caseInsensitiveCompare("cancelAll") == .orderedSame {
            center.removeAllPendingNotificationRequests()
            center.removeAllDeliveredNotifications()
            completion(200, ["success": true], "success")
            return
        }

        if action.caseInsensitiveCompare("areNotificationsEnabled") == .orderedSame {
            center.getNotificationSettings { settings in
                let enabled = (settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional)
                completion(200, ["enabled": enabled], "success")
            }
            return
        }

        completion(404, nil, "Unknown notification action: \(action)")
    }
}
