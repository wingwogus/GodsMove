//
//  Secrets.example.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

// Copy this file to Secrets.swift (gitignored) and fill in real values.
// Secrets.swift is never committed — see root .gitignore.
//
// Wrapped in `#if false`: this project has no per-file target-membership
// exceptions configured on its synchronized group, so this template and a
// real Secrets.swift would otherwise both compile and collide on `enum
// Secrets`. Remove the #if false/#endif in your copied Secrets.swift.
#if false
enum Secrets {
    // 행안부 JUSO API 키 (business.juso.go.kr) — 주소 검색
    static let jusoAPIKey = "REPLACE_ME"

    // V-World API 키 (www.vworld.kr) — 연속지적도/좌표변환/토지특성정보
    static let vWorldAPIKey = "REPLACE_ME"

    // 카카오 Native App Key (developers.kakao.com)
    static let kakaoNativeAppKey = "REPLACE_ME"

    // 네이버 로그인 Client ID/Secret (developers.naver.com)
    static let naverClientID = "REPLACE_ME"
    static let naverClientSecret = "REPLACE_ME"
}
#endif
