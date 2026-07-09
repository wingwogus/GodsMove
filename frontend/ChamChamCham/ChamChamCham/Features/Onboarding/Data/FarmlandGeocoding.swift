//
//  FarmlandGeocoding.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import CoreLocation

protocol FarmlandGeocoding: Sendable {
    func geocode(roadAddress: String) async throws -> CLLocationCoordinate2D
}

protocol ParcelLookup: Sendable {
    func fetchParcel(at coordinate: CLLocationCoordinate2D) async throws -> FarmlandParcel
}
