//
//  IpcPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

public class IpcPlugin: IBridgePlugin {
    public let moduleName = "ipc"

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_0_PUBLIC
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        guard let channel = params["channel"] as? String, !channel.isEmpty else {
            completion(400, nil, "Missing required parameter 'channel'")
            return
        }

        let senderId = params["senderId"] as? String ?? "main"
        let payload = params["payload"]

        switch action {
        case "send", "broadcast":
            let count = IpcHub.shared.broadcast(senderId: senderId, channel: channel, payload: payload)
            completion(200, ["success": true, "recipientCount": count], "success")

        case "sendTo":
            guard let targetId = params["targetId"] as? String, !targetId.isEmpty else {
                completion(400, nil, "Missing required parameter 'targetId'")
                return
            }
            let ok = IpcHub.shared.sendTo(senderId: senderId, targetId: targetId, channel: channel, payload: payload)
            completion(200, ["success": ok, "targetId": targetId], "success")

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'ipc'")
        }
    }
}
