//
//  AppIllustration.swift
//  ChamChamCham
//
//  Created by iyungui on 7/17/26.
//

import SwiftUI

/// Renders a Figma `Illust` asset as an image-slot fallback (e.g. when a record/report thumbnail
/// URL is missing or fails to load). Each illustration ships as two canvases in `Assets.xcassets/Illust`:
/// a square base name (100x100) and a `-1`-suffixed wide name (178x100).
struct AppIllustration: View {
    enum Variant {
        case square
        case wide
    }

    let assetName: String
    var variant: Variant = .square

    var body: some View {
        Image(resolvedAssetName)
            .renderingMode(.original)
            .resizable()
            .scaledToFill()
    }

    private var resolvedAssetName: String {
        switch variant {
        case .square: assetName
        case .wide: "\(assetName)-1"
        }
    }
}
