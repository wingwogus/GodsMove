//
//  CardView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct CardView<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(Spacing.md)
            .background(Color.appBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
