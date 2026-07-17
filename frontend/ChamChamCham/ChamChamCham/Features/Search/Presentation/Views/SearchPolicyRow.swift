//
//  SearchPolicyRow.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// One row in the 정책 정보 search tab (Figma `list`, size `xlarge`). Mirrors the inline row markup
/// in `PolicyListView`, extracted to a file since Search needs it at two call sites (전체 탭 preview
/// + 정책 정보 탭 full list).
struct SearchPolicyRow: View {
    let item: SearchPolicyItem
    var showsDivider: Bool = true

    var body: some View {
        AppListItem(
            size: .xlarge,
            title: item.title,
            organization: item.agencyName,
            infoRows: [
                ("대상자", item.eligibilitySummary),
                ("지원내용", item.benefitSummary),
                ("접수기간", item.applicationPeriodLabel),
            ],
            showsDivider: showsDivider
        )
    }
}
