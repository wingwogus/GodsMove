//
//  LandCharacterAPIService.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

/// 토지 기본정보(면적/지목) 보조 조회 서비스. V-World GetFeature는 면적/지목 속성을 제공하지 않아
/// 등록에 쓸 공식 수치를 이 API로 보완한다. 실패해도 V-World에서 받은 필지 정보로 계속 진행 가능해야 한다.
///
/// data.go.kr 카탈로그에는 "국토교통부_토지특성정보(WMS/WFS/속성정보)"로 등록되어 있지만,
/// 실제 API는 data.go.kr이 아니라 V-World가 호스팅한다 (api.vworld.kr/ned/data/...).
/// 실키로 검증한 결과 별도의 data.go.kr 인증키 없이 기존 V-World 키로 바로 동작했다.
struct LandCharacterAPIService: LandCharacteristicsLookup, Sendable {
    private let baseURL = "https://api.vworld.kr/ned/data/getLandCharacteristics"

    /// 해외 네트워크 경로에서 국내 공공 API가 아예 응답하지 않을 수 있어(App Store 리뷰에서
    /// 확인됨), 기본 60초 타임아웃 대신 짧게 못 박아 실패 상태로 빨리 전환시킨다.
    private static let requestTimeout: TimeInterval = 8

    func fetchLandCharacteristics(pnu: String) async throws -> LandCharacteristics {
        let currentYear = Calendar.current.component(.year, from: Date())
        if let info = try await fetchLandCharacteristics(pnu: pnu, stdrYear: currentYear) {
            return info
        }
        // 당해년도 자료가 아직 고시되지 않았을 수 있어 전년도로 한 번 더 시도한다.
        if let info = try await fetchLandCharacteristics(pnu: pnu, stdrYear: currentYear - 1) {
            return info
        }
        throw FarmLocationAPIError.noData
    }

    private func fetchLandCharacteristics(pnu: String, stdrYear: Int) async throws -> LandCharacteristics? {
        var components = URLComponents(string: baseURL)
        components?.queryItems = [
            URLQueryItem(name: "key", value: Secrets.vWorldAPIKey),
            URLQueryItem(name: "domain", value: Bundle.main.bundleIdentifier ?? ""),
            URLQueryItem(name: "stdrYear", value: String(stdrYear)),
            URLQueryItem(name: "pnu", value: pnu),
            URLQueryItem(name: "format", value: "json")
        ]
        guard let url = components?.url else { throw FarmLocationAPIError.invalidURL }

        let data: Data
        do {
            let request = URLRequest(url: url, timeoutInterval: Self.requestTimeout)
            (data, _) = try await URLSession.shared.data(for: request)
        } catch {
            throw FarmLocationAPIError.network(error.localizedDescription)
        }

        do {
            let decoded = try JSONDecoder().decode(LandCharacterResponse.self, from: data)
            // 동일 pnu가 갱신일자만 다르게 중복으로 오는 경우가 있어, 가장 최근 갱신된 항목을 사용한다.
            guard let field = decoded.landCharacteristicss.field.max(by: { $0.lastUpdtDt < $1.lastUpdtDt }) else {
                return nil
            }
            return LandCharacteristics(
                pnu: field.pnu,
                officialAreaSqm: Double(field.lndpclAr) ?? 0,
                jimokName: field.lndcgrCodeNm
            )
        } catch {
            throw FarmLocationAPIError.decoding(error.localizedDescription)
        }
    }
}

private struct LandCharacterResponse: Decodable {
    let landCharacteristicss: LandCharacteristicsContainer
}

private struct LandCharacteristicsContainer: Decodable {
    let field: [LandCharacteristicsField]
}

private struct LandCharacteristicsField: Decodable {
    let pnu: String
    let lndpclAr: String
    let lndcgrCodeNm: String
    let lastUpdtDt: String
}
