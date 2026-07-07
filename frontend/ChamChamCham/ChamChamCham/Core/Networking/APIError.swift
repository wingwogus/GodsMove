//
//  APIError.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

enum APIError: Error {
    case network(Error)
    case unauthorized
    case validation(message: String)
    case server(statusCode: Int)
    case decoding(Error)
    case apiError(code: String, message: String)
}
