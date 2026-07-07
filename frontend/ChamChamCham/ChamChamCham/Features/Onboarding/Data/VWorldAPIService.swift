//
//  VWorldAPIService.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import CoreLocation
import Foundation

struct VWorldAPIService: FarmlandGeocoding, ParcelLookup, Sendable {
    private let addressBaseURL = "https://api.vworld.kr/req/address"
    private let dataBaseURL = "https://api.vworld.kr/req/data"

    /// V-World 주소 API(req/address, getcoord)로 도로명 주소를 좌표로 변환한다.
    ///
    /// 원래 MapKit의 지오코딩을 사용했으나, 시뮬레이터 환경에서 실제 존재하는 주소도
    /// 전부 실패하는 것을 확인했다 (환경/네트워크 문제로 추정). 이미 검증된 V-World
    /// 키로 같은 주소를 조회하면 정상 동작하므로 MapKit 대신 V-World 주소 API를 사용한다.
    func geocode(roadAddress: String) async throws -> CLLocationCoordinate2D {
        var components = URLComponents(string: addressBaseURL)
        components?.queryItems = [
            URLQueryItem(name: "service", value: "address"),
            URLQueryItem(name: "request", value: "getcoord"),
            URLQueryItem(name: "version", value: "2.0"),
            URLQueryItem(name: "crs", value: "epsg:4326"),
            URLQueryItem(name: "address", value: roadAddress),
            URLQueryItem(name: "refine", value: "true"),
            URLQueryItem(name: "simple", value: "false"),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "type", value: "road"),
            URLQueryItem(name: "key", value: Secrets.vWorldAPIKey)
        ]
        guard let url = components?.url else { throw FarmLocationAPIError.invalidURL }

        let data: Data
        do {
            (data, _) = try await URLSession.shared.data(from: url)
        } catch {
            throw FarmLocationAPIError.network(error.localizedDescription)
        }

        do {
            let decoded = try JSONDecoder().decode(VWorldAddressResponse.self, from: data)
            guard decoded.response.status == "OK", let point = decoded.response.result?.point,
                  let longitude = Double(point.x), let latitude = Double(point.y) else {
                throw FarmLocationAPIError.noResult
            }
            return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        } catch let error as FarmLocationAPIError {
            throw error
        } catch {
            throw FarmLocationAPIError.decoding(error.localizedDescription)
        }
    }

    func fetchParcel(at coordinate: CLLocationCoordinate2D) async throws -> FarmlandParcel {
        var components = URLComponents(string: dataBaseURL)
        components?.queryItems = [
            URLQueryItem(name: "service", value: "data"),
            URLQueryItem(name: "request", value: "GetFeature"),
            URLQueryItem(name: "data", value: "LP_PA_CBND_BUBUN"),
            URLQueryItem(name: "key", value: Secrets.vWorldAPIKey),
            URLQueryItem(name: "domain", value: Bundle.main.bundleIdentifier ?? ""),
            URLQueryItem(name: "geomFilter", value: "POINT(\(coordinate.longitude) \(coordinate.latitude))"),
            URLQueryItem(name: "geometry", value: "true"),
            URLQueryItem(name: "attribute", value: "true"),
            URLQueryItem(name: "format", value: "json")
        ]
        guard let url = components?.url else { throw FarmLocationAPIError.invalidURL }

        let data: Data
        do {
            (data, _) = try await URLSession.shared.data(from: url)
        } catch {
            throw FarmLocationAPIError.network(error.localizedDescription)
        }

        do {
            let decoded = try JSONDecoder().decode(VWorldFeatureResponse.self, from: data)
            guard let feature = decoded.response.result?.featureCollection.features.first else {
                throw FarmLocationAPIError.noParcelFound
            }
            return feature.toFarmlandParcel()
        } catch let error as FarmLocationAPIError {
            throw error
        } catch {
            throw FarmLocationAPIError.decoding(error.localizedDescription)
        }
    }
}

// MARK: - Geocoding response

private struct VWorldAddressResponse: Decodable {
    let response: VWorldAddressResponseBody
}

private struct VWorldAddressResponseBody: Decodable {
    let status: String
    let result: VWorldAddressResult?
}

private struct VWorldAddressResult: Decodable {
    let point: VWorldAddressPoint
}

private struct VWorldAddressPoint: Decodable {
    let x: String
    let y: String
}

// MARK: - Parcel (GetFeature) response
//
// V-World GetFeature(format=json) 응답은 GeoJSON과 유사한 형태로
// response.result.featureCollection.features 아래에 지오메트리/속성이 온다.

private struct VWorldFeatureResponse: Decodable {
    let response: VWorldResponseBody
}

private struct VWorldResponseBody: Decodable {
    let status: String
    let result: VWorldResult?
}

private struct VWorldResult: Decodable {
    let featureCollection: VWorldFeatureCollection
}

private struct VWorldFeatureCollection: Decodable {
    let features: [VWorldFeature]
}

private struct VWorldFeature: Decodable {
    let geometry: VWorldGeometry
    let properties: VWorldProperties

    func toFarmlandParcel() -> FarmlandParcel {
        let coordinates = geometry.outerRingCoordinates
        return FarmlandParcel(
            pnu: properties.pnu,
            jibunAddr: properties.jibunAddr,
            jimok: properties.jimok,
            areaSqm: FarmlandParcel.planarArea(of: coordinates),
            coordinates: coordinates
        )
    }
}

// 실제 응답은 항상 "MultiPolygon"(4단계 중첩)으로 온다. 스펙 문서가 가정한
// "Polygon"(3단계)은 실제 키로 curl 검증한 결과 관측되지 않았지만, 혹시 다른
// 레이어에서 Polygon으로 올 경우를 대비해 폴백으로 남겨둔다.
private struct VWorldGeometry: Decodable {
    let type: String
    let outerRingCoordinates: [GeoPoint]

    private enum CodingKeys: String, CodingKey {
        case type, coordinates
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        type = try container.decodeIfPresent(String.self, forKey: .type) ?? ""

        func toCoordinates(_ ring: [[Double]]) -> [GeoPoint] {
            ring.compactMap { point in
                guard point.count >= 2 else { return nil }
                return GeoPoint(latitude: point[1], longitude: point[0])
            }
        }

        if let multiPolygon = try? container.decode([[[[Double]]]].self, forKey: .coordinates),
           let firstPolygon = multiPolygon.first,
           let outerRing = firstPolygon.first {
            outerRingCoordinates = toCoordinates(outerRing)
        } else if let polygon = try? container.decode([[[Double]]].self, forKey: .coordinates),
                  let outerRing = polygon.first {
            outerRingCoordinates = toCoordinates(outerRing)
        } else {
            outerRingCoordinates = []
        }
    }
}

// 실제 응답 필드는 {pnu, jibun, bonbun, bubun, addr, jiga, gosi_year, gosi_month}뿐이며
// jimok/area 필드는 존재하지 않는다. jimok은 jibun 문자열 끝에 붙는 한 글자
// 지목 약자(예: "70-1대", "383-5 전")를 파싱해서 얻는다.
private struct VWorldProperties: Decodable {
    let pnu: String
    let jibunAddr: String
    let jimok: String

    private enum CodingKeys: String, CodingKey {
        case pnu, jibun, addr
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        pnu = try container.decodeIfPresent(String.self, forKey: .pnu) ?? ""
        jibunAddr = try container.decodeIfPresent(String.self, forKey: .addr) ?? ""
        let jibun = try container.decodeIfPresent(String.self, forKey: .jibun) ?? ""
        jimok = VWorldProperties.parseJimok(from: jibun)
    }

    private static func parseJimok(from jibun: String) -> String {
        guard let last = jibun.trimmingCharacters(in: .whitespaces).last, last.isLetter else {
            return ""
        }
        return String(last)
    }
}
