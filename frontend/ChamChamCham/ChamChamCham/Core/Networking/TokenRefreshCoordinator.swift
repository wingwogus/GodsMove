//
//  TokenRefreshCoordinator.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

actor TokenRefreshCoordinator {
    private var inFlightRefresh: Task<Void, Error>?

    func refreshIfNeeded() async throws {
        fatalError("not implemented")
    }
}
