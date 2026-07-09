//
//  FarmlandParcel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct FarmlandParcel: Hashable, Sendable {
    let pnu: String
    let jibunAddr: String
    var jimok: String
    var areaSqm: Double
    let coordinates: [GeoPoint]

    /// 지목 28종 (공간정보관리법 시행령 제58조) 한 글자 약자 표기
    private static let jimokNames: [String: String] = [
        "전": "전", "답": "답", "과": "과수원", "목": "목장용지", "임": "임야",
        "광": "광천지", "염": "염전", "대": "대지", "장": "공장용지", "학": "학교용지",
        "차": "주차장", "주": "주유소용지", "창": "창고용지", "도": "도로", "철": "철도용지",
        "제": "제방", "천": "하천", "구": "구거", "유": "유지", "양": "양어장",
        "수": "수도용지", "공": "공원", "체": "체육용지", "원": "유원지", "종": "종교용지",
        "사": "사적지", "묘": "묘지", "잡": "잡종지"
    ]

    var jimokName: String {
        FarmlandParcel.jimokNames[jimok] ?? jimok
    }

    /// V-World GetFeature 응답에는 면적(area) 속성이 없어, 폴리곤 좌표로부터
    /// 등거리원통도법(equirectangular) 투영 후 Shoelace 공식으로 직접 계산한다.
    static func planarArea(of coordinates: [GeoPoint]) -> Double {
        guard coordinates.count >= 3 else { return 0 }
        let earthRadius = 6_378_137.0
        let refLatRad = (coordinates.reduce(0) { $0 + $1.latitude } / Double(coordinates.count)) * .pi / 180

        let points = coordinates.map { coord -> (x: Double, y: Double) in
            let x = earthRadius * (coord.longitude * .pi / 180) * cos(refLatRad)
            let y = earthRadius * (coord.latitude * .pi / 180)
            return (x, y)
        }

        var sum = 0.0
        for i in 0..<points.count {
            let j = (i + 1) % points.count
            sum += points[i].x * points[j].y - points[j].x * points[i].y
        }
        return abs(sum) / 2
    }

    var pyeong: Double {
        areaSqm / 3.3058
    }

    var formattedArea: String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        let sqmText = formatter.string(from: NSNumber(value: areaSqm)) ?? "\(Int(areaSqm))"
        let pyeongText = formatter.string(from: NSNumber(value: pyeong)) ?? "\(Int(pyeong))"
        return "\(sqmText)㎡ (약 \(pyeongText)평)"
    }
}
