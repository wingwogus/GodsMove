//
//  ReportRoute.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

enum ReportRoute: Hashable {
    case detail(WorkReportKey)
    case recordHistory(WorkReportKey)
}
