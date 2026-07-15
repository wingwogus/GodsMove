//
//  WeatherIconMapping.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

/// Maps the backend's raw `weatherCondition` string to an `Assets.xcassets/icon` glyph name.
/// The backend enum itself isn't documented (C-2, docs/figma/record/2026-07-13-record-backend-conflicts.md
/// already flags this same gap for the Record tab) — matching is done by keyword so it degrades
/// gracefully instead of failing to compile/decode. Falls back to `clear_day` when nothing matches.
enum WeatherIconMapping {
    static func assetName(for condition: String) -> String {
        let normalized = condition.uppercased()
        if normalized.contains("SNOW") { return "snowflake" }
        if normalized.contains("RAIN") { return "rainy" }
        if normalized.contains("CLOUDY") || normalized.contains("OVERCAST") { return "cloudy" }
        if normalized.contains("CLOUD") { return "cloud" }
        if normalized.contains("CLEAR") || normalized.contains("SUNNY") { return "clear_day" }
        return "clear_day"
    }
}
