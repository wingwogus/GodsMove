//
//  ReachabilityMonitor.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation
import Network
import Observation

@Observable
final class ReachabilityMonitor {
    private(set) var isConnected = true
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "ReachabilityMonitor")

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                self?.isConnected = path.status == .satisfied
            }
        }
        monitor.start(queue: queue)
    }

    func stop() {
        monitor.cancel()
    }
}
