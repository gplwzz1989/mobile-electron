//
//  IBridgePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

public protocol IBridgePlugin: AnyObject {
    var moduleName: String { get }
    func getRequiredTier(action: String) -> Int
    func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (_ code: Int, _ data: [String: Any]?, _ message: String) -> Void
    )
}
