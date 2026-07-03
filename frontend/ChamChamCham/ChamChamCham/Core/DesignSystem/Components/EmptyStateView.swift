//
//  EmptyStateView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct EmptyStateView: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.appBody)
            .foregroundStyle(Color.appTextSecondary)
    }
}
