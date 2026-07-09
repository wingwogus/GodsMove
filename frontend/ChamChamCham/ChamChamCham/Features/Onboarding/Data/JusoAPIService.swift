//
//  JusoAPIService.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct JusoAPIService: AddressSearching, Sendable {
    private let baseURL = "https://business.juso.go.kr/addrlink/addrLinkApi.do"

    func search(keyword: String) async throws -> [JusoAddress] {
        guard !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }

        var components = URLComponents(string: baseURL)
        components?.queryItems = [
            URLQueryItem(name: "confmKey", value: Secrets.jusoAPIKey),
            URLQueryItem(name: "keyword", value: keyword),
            URLQueryItem(name: "resultType", value: "json"),
            URLQueryItem(name: "countPerPage", value: "20"),
            URLQueryItem(name: "currentPage", value: "1")
        ]
        guard let url = components?.url else { throw FarmLocationAPIError.invalidURL }

        let data: Data
        do {
            (data, _) = try await URLSession.shared.data(from: url)
        } catch {
            throw FarmLocationAPIError.network(error.localizedDescription)
        }

        do {
            let decoded = try JSONDecoder().decode(JusoAPIResponse.self, from: data)
            let common = decoded.results.common
            guard common.errorCode == "0" else {
                throw FarmLocationAPIError.api(code: common.errorCode, message: common.errorMessage)
            }
            return decoded.results.juso
        } catch let error as FarmLocationAPIError {
            throw error
        } catch {
            throw FarmLocationAPIError.decoding(error.localizedDescription)
        }
    }
}

private struct JusoAPIResponse: Decodable {
    let results: JusoResultsContainer
}

private struct JusoResultsContainer: Decodable {
    let common: JusoCommon
    let juso: [JusoAddress]
}

private struct JusoCommon: Decodable {
    let errorCode: String
    let errorMessage: String
}
