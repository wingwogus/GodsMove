//
//  WeatherDetailView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import SwiftUI

/// 날씨 상세 (Figma `홈 -> 날씨 상세`). `GET /weather/detail` 실데이터 — 선택 소스 조회에 실패한 값은
/// backend가 null로 내려주고, 이 화면은 그 값들을 "-"로 표시한다.
struct WeatherDetailView: View {
    let state: HomeSectionState<WeatherDetail>
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "날씨 상세",
                isDetail: true,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
            )
            ScrollView {
                content
                    .padding(.horizontal, 20)
                    .padding(.vertical, Spacing.md)
            }
            .background(Color.Background.default)
        }
        .navigationBarHidden(true)
    }

    @ViewBuilder private var content: some View {
        switch state {
        case .loading:
            ProgressView().frame(maxWidth: .infinity, minHeight: 300)
        case let .loaded(detail):
            VStack(alignment: .leading, spacing: Spacing.lg) {
                todaySection(detail)
                tipBanner
                detailGrid(detail)
                Divider().foregroundStyle(Color.Border.subtle)
                weeklyForecast(detail.forecast)
            }
        case let .failed(message):
            Text(message)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .frame(maxWidth: .infinity, minHeight: 300, alignment: .center)
        }
    }

    private func todaySection(_ detail: WeatherDetail) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.md) {
                AppIconView(source: .asset(WeatherIconMapping.assetName(for: detail.condition.code)), size: 96, renderingMode: .original)
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(detail.temperature)°")
                        .appTypography(.headlineLargeEmphasized)
                        .foregroundStyle(Color.Text.default)
                    HStack(spacing: 4) {
                        Text("체감 \(optionalDegree(detail.feelsLikeTemperature))")
                        Text("|")
                        Text("최저 \(optionalDegree(detail.minTemperature)) - 최고 \(optionalDegree(detail.maxTemperature))")
                    }
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.subtle)
                }
            }
            Text(detail.address)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
        }
    }

    private var tipBanner: some View {
        Text("오늘은 관수하기 좋은 날씨에요!")
            .appTypography(.bodyLarge)
            .foregroundStyle(Color.Text.primary)
            .frame(maxWidth: .infinity)
            .padding(Spacing.md)
            .background(Color.Object.secondary)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func detailGrid(_ detail: WeatherDetail) -> some View {
        let columns = [GridItem(.flexible(), spacing: Spacing.sm), GridItem(.flexible(), spacing: Spacing.sm)]
        return LazyVGrid(columns: columns, spacing: Spacing.sm) {
            detailCard(label: "자외선 지수", value: detail.uvIndexLabel)
            detailCard(label: "강수확률", value: optionalPercent(detail.precipitationProbabilityPercent))
            detailCard(label: "습도", value: optionalPercent(detail.humidityPercent))
            detailCard(label: "풍속", value: detail.windSpeedMps.map { String(format: "%.1fm/s", $0) } ?? "-")
        }
    }

    private func detailCard(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
            Text(value)
                .appTypography(.titleMediumEmphasized)
                .foregroundStyle(Color.Text.subtle)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, minHeight: 93, alignment: .topLeading)
        .background(Color.Object.default)
        .overlay {
            RoundedRectangle(cornerRadius: 12).stroke(Color.Border.default, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func weeklyForecast(_ days: [WeatherForecastDay]) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text("주간 예보")
                .appTypography(.titleMediumEmphasized)
                .foregroundStyle(Color.Text.subtle)
            HStack {
                ForEach(days) { day in
                    VStack(spacing: Spacing.xs) {
                        Text(day.dayLabel)
                            .appTypography(day.dayLabel == "오늘" ? .labelMediumEmphasized : .labelMedium)
                            .foregroundStyle(day.dayLabel == "오늘" ? Color.Text.default : Color.Text.subtle)
                        AppIconView(source: .asset(WeatherIconMapping.assetName(for: day.condition.code)), size: 40, renderingMode: .original)
                        Text(optionalDegree(day.temperature))
                            .appTypography(.bodyMedium)
                            .foregroundStyle(Color.Text.default)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Background.subtle)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func optionalDegree(_ value: Int?) -> String {
        value.map { "\($0)°" } ?? "-"
    }

    private func optionalPercent(_ value: Int?) -> String {
        value.map { "\($0)%" } ?? "-"
    }
}
