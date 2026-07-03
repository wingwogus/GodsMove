//
//  ScreenPathRouter.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Observation
import SwiftUI

@Observable
final class ScreenPathRouter {
    var path = NavigationPath()

    func push(_ route: some Hashable) {
        path.append(route)
    }

    func pop() {
        guard !path.isEmpty else { return }
        path.removeLast()
    }

    func popToRoot() {
        path.removeLast(path.count)
    }
}
