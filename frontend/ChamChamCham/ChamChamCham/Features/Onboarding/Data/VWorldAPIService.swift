//
//  VWorldAPIService.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import CoreLocation
import Foundation

struct VWorldAPIService: FarmlandGeocoding, ParcelLookup, ReverseGeocoding, Sendable {
    private let addressBaseURL = "https://api.vworld.kr/req/address"
    private let dataBaseURL = "https://api.vworld.kr/req/data"

    /// 해외 네트워크 경로에서 국내 공공 API가 아예 응답하지 않을 수 있어(App Store 리뷰에서
    /// 확인됨), 기본 60초 타임아웃 대신 짧게 못 박아 실패 상태로 빨리 전환시킨다.
    private static let requestTimeout: TimeInterval = 8

    /// V-World 주소 API(req/address, getcoord)로 주소를 좌표로 변환한다.
    ///
    /// 원래 MapKit의 지오코딩을 사용했으나, 시뮬레이터 환경에서 실제 존재하는 주소도
    /// 전부 실패하는 것을 확인했다 (환경/네트워크 문제로 추정). 이미 검증된 V-World
    /// 키로 같은 주소를 조회하면 정상 동작하므로 MapKit 대신 V-World 주소 API를 사용한다.
    ///
    /// 도로명(`type=road`)·지번(`type=parcel`)을 동시에 요청해 성공한 쪽(도로명 우선)을
    /// 반환한다. 산골·하천변 농지는 도로명이 없어 `roadAddress`가 비는 경우가 많은데, 이때
    /// 지번 폴백이 없으면 정상 주소도 좌표 변환에 실패한다(App Store 리뷰에서 관측된 버그).
    /// 순차 시도 시 최악의 경우 두 번의 타임아웃이 누적되므로 병행 호출로 대기 시간을 줄인다.
    func geocode(roadAddress: String, jibunAddress: String) async throws -> CLLocationCoordinate2D {
        let trimmedRoad = roadAddress.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedJibun = jibunAddress.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedRoad.isEmpty || !trimmedJibun.isEmpty else {
            throw FarmLocationAPIError.noResult
        }

        async let roadTask: CLLocationCoordinate2D? = trimmedRoad.isEmpty
            ? nil
            : try? await getcoord(address: trimmedRoad, type: "road")
        async let jibunTask: CLLocationCoordinate2D? = trimmedJibun.isEmpty
            ? nil
            : try? await getcoord(address: trimmedJibun, type: "parcel")

        if let road = await roadTask {
            return road
        }
        if let jibun = await jibunTask {
            return jibun
        }
        throw FarmLocationAPIError.noResult
    }

    /// V-World getcoord 단일 요청. `type`은 "road"(도로명) 또는 "parcel"(지번).
    private func getcoord(address: String, type: String) async throws -> CLLocationCoordinate2D {
        var components = URLComponents(string: addressBaseURL)
        components?.queryItems = [
            URLQueryItem(name: "service", value: "address"),
            URLQueryItem(name: "request", value: "getcoord"),
            URLQueryItem(name: "version", value: "2.0"),
            URLQueryItem(name: "crs", value: "epsg:4326"),
            URLQueryItem(name: "address", value: address),
            URLQueryItem(name: "refine", value: "true"),
            URLQueryItem(name: "simple", value: "false"),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "type", value: type),
            URLQueryItem(name: "key", value: Secrets.vWorldAPIKey),
            URLQueryItem(name: "domain", value: Bundle.main.bundleIdentifier ?? "")
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
            let request = URLRequest(url: url, timeoutInterval: Self.requestTimeout)
            (data, _) = try await URLSession.shared.data(for: request)
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

    /// V-World 주소 API(req/address, getAddress)로 좌표를 도로명·지번 주소로 변환한다.
    ///
    /// 도로명(`type=road`)과 지번(`type=parcel`)을 병행 조회한다. 하천둑·산골 농지처럼
    /// 도로명이 없는 좌표는 `type=road`가 NOT_FOUND를 반환하는데, 이는 실패가 아니라
    /// "도로명 없음"이므로 해당 필드만 nil로 남기고 던지지 않는다. 둘 다 네트워크/디코딩으로
    /// 실패한 경우에만 에러를 던진다.
    func reverseGeocode(at coordinate: CLLocationCoordinate2D) async throws -> ReverseGeocodedAddress {
        async let roadTask = requestAddress(at: coordinate, type: "road")
        async let parcelTask = requestAddress(at: coordinate, type: "parcel")
        let road = await roadTask
        let parcel = await parcelTask

        if road.hardFailure && parcel.hardFailure {
            throw FarmLocationAPIError.noResult
        }
        return ReverseGeocodedAddress(roadAddress: road.text, jibunAddress: parcel.text)
    }

    /// 한 타입(road/parcel)에 대한 역지오코딩 요청. NOT_FOUND는 `text=nil, hardFailure=false`,
    /// 네트워크/디코딩 실패는 `hardFailure=true`로 표현해 상위에서 타입별로 관용 처리하게 한다.
    private func requestAddress(at coordinate: CLLocationCoordinate2D, type: String) async -> AddressLookupOutcome {
        var components = URLComponents(string: addressBaseURL)
        components?.queryItems = [
            URLQueryItem(name: "service", value: "address"),
            URLQueryItem(name: "request", value: "getAddress"),
            URLQueryItem(name: "version", value: "2.0"),
            URLQueryItem(name: "crs", value: "epsg:4326"),
            URLQueryItem(name: "point", value: "\(coordinate.longitude),\(coordinate.latitude)"),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "type", value: type),
            URLQueryItem(name: "key", value: Secrets.vWorldAPIKey)
        ]
        guard let url = components?.url else {
            return AddressLookupOutcome(text: nil, hardFailure: true)
        }

        do {
            let request = URLRequest(url: url, timeoutInterval: Self.requestTimeout)
            let (data, _) = try await URLSession.shared.data(for: request)
            let decoded = try JSONDecoder().decode(VWorldGetAddressResponse.self, from: data)
            guard decoded.response.status == "OK" else {
                // NOT_FOUND / ERROR 등: 해당 타입 주소가 없을 뿐 요청 자체는 성공.
                return AddressLookupOutcome(text: nil, hardFailure: false)
            }
            let text = decoded.response.result?.first?.text?.trimmingCharacters(in: .whitespaces)
            return AddressLookupOutcome(text: (text?.isEmpty == false) ? text : nil, hardFailure: false)
        } catch {
            return AddressLookupOutcome(text: nil, hardFailure: true)
        }
    }
}

private struct AddressLookupOutcome: Sendable {
    let text: String?
    let hardFailure: Bool
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

// MARK: - Reverse-geocoding (getAddress) response
//
// getcoord와 달리 response.result가 배열이고 각 원소에 text(주소 문자열)가 온다.
// zipcode/type/structure 필드는 사용하지 않아 디코딩을 생략한다.

private struct VWorldGetAddressResponse: Decodable {
    let response: VWorldGetAddressBody
}

private struct VWorldGetAddressBody: Decodable {
    let status: String
    let result: [VWorldGetAddressResult]?
}

private struct VWorldGetAddressResult: Decodable {
    let text: String?
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
