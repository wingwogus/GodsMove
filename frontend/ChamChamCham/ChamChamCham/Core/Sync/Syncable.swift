//
//  Syncable.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

protocol Syncable {
    var localID: UUID { get }
    var serverID: UUID? { get set }
    var syncStatus: SyncStatus { get set }
}

enum SyncStatus: String, Codable {
    case pendingCreate
    case pendingUpdate
    case synced
    case syncFailed
}
