//
//  FloatingWindowManager.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import UIKit
import WebKit

public class FloatingWindowManager: NSObject {

    public static let shared = FloatingWindowManager()

    private class FloatingWindowHolder {
        var id: String
        var url: String
        var title: String
        var width: CGFloat
        var height: CGFloat
        var currentX: CGFloat
        var currentY: CGFloat
        var isMinimized: Bool = false
        var isVisible: Bool = true
        var draggable: Bool = true

        var windowContainer: UIView?
        var miniBubble: UIView?
        var webView: WKWebView?
        var titleLabel: UILabel?

        init(id: String, url: String, title: String, width: CGFloat, height: CGFloat, x: CGFloat, y: CGFloat, draggable: Bool) {
            self.id = id
            self.url = url
            self.title = title
            self.width = width
            self.height = height
            self.currentX = x
            self.currentY = y
            self.draggable = draggable
        }
    }

    private var activeWindows: [String: FloatingWindowHolder] = [:]

    private override init() {
        super.init()
    }

    /**
     * 创建并在父视图中挂载画中画悬浮窗
     */
    public func createFloatingWindow(
        in parentView: UIView,
        options: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        DispatchQueue.main.async {
            var id = options["id"] as? String ?? ""
            if id.trimmingCharacters(in: .whitespaces).isEmpty {
                id = "float_\(UUID().uuidString.prefix(8).lowercased())"
            }

            // 若已存在同名窗口，先销毁
            if self.activeWindows[id] != nil {
                _ = self.closeFloatingWindow(id: id)
            }

            let urlStr = options["url"] as? String ?? ""
            let title = options["title"] as? String ?? "悬浮画中画"
            let width = CGFloat(options["width"] as? Double ?? 280.0)
            let height = CGFloat(options["height"] as? Double ?? 200.0)
            let draggable = options["draggable"] as? Bool ?? true
            let showControls = options["showControls"] as? Bool ?? true

            let screenBounds = parentView.bounds
            let defaultX = max(16, screenBounds.width - width - 20)
            let defaultY: CGFloat = 80.0

            let initialX = CGFloat(options["x"] as? Double ?? Double(defaultX))
            let initialY = CGFloat(options["y"] as? Double ?? Double(defaultY))

            let holder = FloatingWindowHolder(
                id: id,
                url: urlStr,
                title: title,
                width: width,
                height: height,
                x: initialX,
                y: initialY,
                draggable: draggable
            )

            // 1. 构建主悬浮容器卡片
            let container = UIView(frame: CGRect(x: initialX, y: initialY, width: width, height: height))
            container.backgroundColor = .white
            container.layer.cornerRadius = 12
            container.layer.borderWidth = 1.0
            container.layer.borderColor = UIColor(red: 0.88, green: 0.91, blue: 0.94, alpha: 1.0).cgColor
            container.layer.shadowColor = UIColor.black.cgColor
            container.layer.shadowOffset = CGSize(width: 0, height: 4)
            container.layer.shadowRadius = 8
            container.layer.shadowOpacity = 0.15
            container.clipsToBounds = false

            // 裁剪内部内容圆角
            let innerWrapper = UIView(frame: container.bounds)
            innerWrapper.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            innerWrapper.layer.cornerRadius = 12
            innerWrapper.clipsToBounds = true
            container.addSubview(innerWrapper)

            // 2. 顶部控制栏
            let headerHeight: CGFloat = 34.0
            let headerView = UIView(frame: CGRect(x: 0, y: 0, width: width, height: headerHeight))
            headerView.backgroundColor = UIColor(red: 0.97, green: 0.98, blue: 0.99, alpha: 1.0)
            innerWrapper.addSubview(headerView)

            let titleLabel = UILabel(frame: CGRect(x: 10, y: 0, width: width - 70, height: headerHeight))
            titleLabel.text = title
            titleLabel.font = UIFont.systemFont(ofSize: 12, weight: .medium)
            titleLabel.textColor = UIColor(red: 0.12, green: 0.16, blue: 0.23, alpha: 1.0)
            headerView.addSubview(titleLabel)

            // 最小化按钮
            let minBtn = UIButton(type: .system)
            minBtn.frame = CGRect(x: width - 60, y: 0, width: 28, height: headerHeight)
            minBtn.setTitle("—", for: .normal)
            minBtn.setTitleColor(UIColor.gray, for: .normal)
            minBtn.titleLabel?.font = UIFont.systemFont(ofSize: 12, weight: .bold)
            let capturedId = id
            minBtn.addAction(UIAction { [weak self] _ in
                _ = self?.minimizeFloatingWindow(id: capturedId, minimize: true)
            }, for: .touchUpInside)
            headerView.addSubview(minBtn)

            // 关闭按钮
            let closeBtn = UIButton(type: .system)
            closeBtn.frame = CGRect(x: width - 30, y: 0, width: 28, height: headerHeight)
            closeBtn.setTitle("✕", for: .normal)
            closeBtn.setTitleColor(UIColor.systemRed, for: .normal)
            closeBtn.titleLabel?.font = UIFont.systemFont(ofSize: 12, weight: .bold)
            closeBtn.addAction(UIAction { [weak self] _ in
                _ = self?.closeFloatingWindow(id: capturedId)
            }, for: .touchUpInside)
            headerView.addSubview(closeBtn)

            if !showControls {
                headerView.isHidden = true
            }

            // 3. 独立内嵌 WKWebView
            let webTop = showControls ? headerHeight : 0
            let webFrame = CGRect(x: 0, y: webTop, width: width, height: height - webTop)
            let config = WKWebViewConfiguration()
            config.allowsInlineMediaPlayback = true
            let childWebView = WKWebView(frame: webFrame, configuration: config)
            childWebView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            if let validUrl = URL(string: urlStr), !urlStr.isEmpty {
                childWebView.load(URLRequest(url: validUrl))
            }
            innerWrapper.addSubview(childWebView)

            // 4. 最小化微型悬浮球 (Mini Bubble)
            let bubbleSize: CGFloat = 48.0
            let miniBubble = UIView(frame: CGRect(x: initialX, y: initialY, width: bubbleSize, height: bubbleSize))
            miniBubble.backgroundColor = UIColor(red: 0.23, green: 0.51, blue: 0.96, alpha: 1.0)
            miniBubble.layer.cornerRadius = bubbleSize / 2
            miniBubble.layer.shadowColor = UIColor.black.cgColor
            miniBubble.layer.shadowOffset = CGSize(width: 0, height: 4)
            miniBubble.layer.shadowRadius = 6
            miniBubble.layer.shadowOpacity = 0.2
            miniBubble.isHidden = true

            let bubbleLabel = UILabel(frame: miniBubble.bounds)
            bubbleLabel.text = "🗔"
            bubbleLabel.textColor = .white
            bubbleLabel.textAlignment = .center
            bubbleLabel.font = UIFont.systemFont(ofSize: 18)
            miniBubble.addSubview(bubbleLabel)

            let bubbleTap = UITapGestureRecognizer(target: self, action: #selector(self.handleBubbleTap(_:)))
            miniBubble.addGestureRecognizer(bubbleTap)
            miniBubble.isUserInteractionEnabled = true

            // 5. 拖拽手势
            if draggable {
                self.setupPanGesture(for: container, parentView: parentView, holder: holder)
                self.setupPanGesture(for: miniBubble, parentView: parentView, holder: holder)
            }

            parentView.addSubview(container)
            parentView.addSubview(miniBubble)

            holder.windowContainer = container
            holder.miniBubble = miniBubble
            holder.webView = childWebView
            holder.titleLabel = titleLabel

            self.activeWindows[id] = holder

            completion(200, [
                "success": true,
                "id": id,
                "width": Double(width),
                "height": Double(height),
                "x": Double(initialX),
                "y": Double(initialY)
            ], "success")
        }
    }

    @objc private func handleBubbleTap(_ recognizer: UITapGestureRecognizer) {
        guard let bubble = recognizer.view else { return }
        for (id, holder) in activeWindows {
            if holder.miniBubble == bubble {
                _ = minimizeFloatingWindow(id: id, minimize: false)
                break
            }
        }
    }

    private func setupPanGesture(for targetView: UIView, parentView: UIView, holder: FloatingWindowHolder) {
        var startCenter = CGPoint.zero
        let pan = UIPanGestureRecognizer { recognizer in
            guard let panGesture = recognizer as? UIPanGestureRecognizer else { return }
            let translation = panGesture.translation(in: parentView)

            switch panGesture.state {
            case .began:
                startCenter = targetView.center
            case .changed:
                let newX = startCenter.x + translation.x
                let newY = startCenter.y + translation.y
                targetView.center = CGPoint(x: newX, y: newY)
                holder.currentX = targetView.frame.origin.x
                holder.currentY = targetView.frame.origin.y
            case .ended, .cancelled:
                // 边界吸附限制
                let parentBounds = parentView.bounds
                var currentFrame = targetView.frame

                currentFrame.origin.x = max(8, min(currentFrame.origin.x, parentBounds.width - currentFrame.width - 8))
                currentFrame.origin.y = max(44, min(currentFrame.origin.y, parentBounds.height - currentFrame.height - 44))

                let snapToRight = (currentFrame.midX > parentBounds.midX)
                let targetX: CGFloat = snapToRight ? (parentBounds.width - currentFrame.width - 12) : 12

                UIView.animate(withDuration: 0.2) {
                    targetView.frame.origin.x = targetX
                    targetView.frame.origin.y = currentFrame.origin.y
                }
                holder.currentX = targetX
                holder.currentY = currentFrame.origin.y
            default:
                break
            }
        }
        targetView.addGestureRecognizer(pan)
    }

    /**
     * 关闭指定悬浮窗
     */
    public func closeFloatingWindow(id: String?) -> Bool {
        let targetId = id ?? activeWindows.keys.first
        guard let wId = targetId, let holder = activeWindows.removeValue(forKey: wId) else {
            return false
        }

        DispatchQueue.main.async {
            holder.windowContainer?.removeFromSuperview()
            holder.miniBubble?.removeFromSuperview()
            holder.webView?.stopLoading()
            holder.webView = nil
        }
        return true
    }

    /**
     * 最小化 / 还原
     */
    public func minimizeFloatingWindow(id: String, minimize: Bool) -> Bool {
        guard let holder = activeWindows[id] else { return false }
        holder.isMinimized = minimize

        DispatchQueue.main.async {
            if minimize {
                holder.windowContainer?.isHidden = true
                if let bubble = holder.miniBubble {
                    bubble.isHidden = false
                    bubble.frame.origin = CGPoint(x: holder.currentX, y: holder.currentY)
                }
            } else {
                holder.miniBubble?.isHidden = true
                if let container = holder.windowContainer {
                    container.isHidden = !holder.isVisible
                    container.frame.origin = CGPoint(x: holder.currentX, y: holder.currentY)
                }
            }
        }
        return true
    }

    /**
     * 显示 / 隐藏
     */
    public func setFloatingWindowVisible(id: String, visible: Bool) -> Bool {
        guard let holder = activeWindows[id] else { return false }
        holder.isVisible = visible

        DispatchQueue.main.async {
            if !visible {
                holder.windowContainer?.isHidden = true
                holder.miniBubble?.isHidden = true
            } else {
                if holder.isMinimized {
                    holder.miniBubble?.isHidden = false
                } else {
                    holder.windowContainer?.isHidden = false
                }
            }
        }
        return true
    }

    /**
     * 更新尺寸与位置
     */
    public func updateFloatingWindow(id: String, options: [String: Any]) -> Bool {
        guard let holder = activeWindows[id] else { return false }

        DispatchQueue.main.async {
            if let w = options["width"] as? Double { holder.width = CGFloat(w) }
            if let h = options["height"] as? Double { holder.height = CGFloat(h) }
            if let x = options["x"] as? Double { holder.currentX = CGFloat(x) }
            if let y = options["y"] as? Double { holder.currentY = CGFloat(y) }

            if let container = holder.windowContainer {
                container.frame = CGRect(x: holder.currentX, y: holder.currentY, width: holder.width, height: holder.height)
            }
            if let bubble = holder.miniBubble {
                bubble.frame.origin = CGPoint(x: holder.currentX, y: holder.currentY)
            }
        }
        return true
    }

    /**
     * 执行 JS 脚本
     */
    public func evalFloatingWindow(id: String, script: String, completion: @escaping (Any?, String?) -> Void) {
        guard let holder = activeWindows[id], let webView = holder.webView else {
            completion(nil, "Window not found: \(id)")
            return
        }

        DispatchQueue.main.async {
            webView.evaluateJavaScript(script) { result, error in
                if let err = error {
                    completion(nil, err.localizedDescription)
                } else {
                    completion(result, nil)
                }
            }
        }
    }

    /**
     * 获取浮窗状态
     */
    public func getFloatingWindowState(id: String?) -> [String: Any]? {
        let targetId = id ?? activeWindows.keys.first
        guard let wId = targetId, let holder = activeWindows[wId] else {
            return nil
        }

        return [
            "id": holder.id,
            "url": holder.url,
            "title": holder.title,
            "width": Double(holder.width),
            "height": Double(holder.height),
            "x": Double(holder.currentX),
            "y": Double(holder.currentY),
            "isMinimized": holder.isMinimized,
            "isVisible": holder.isVisible
        ]
    }

    public func closeAll() {
        for key in Array(activeWindows.keys) {
            _ = closeFloatingWindow(id: key)
        }
    }
}
