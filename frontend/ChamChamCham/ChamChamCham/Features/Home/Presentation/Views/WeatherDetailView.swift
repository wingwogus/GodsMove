//
//  WeatherDetailView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import SwiftUI

/// 날씨 상세 (Figma `홈 -> 날씨 상세`). `temperature`/`condition` are real (`GET /farms/{id}/weather`);
/// every other field (체감/최저·최고/자외선/강수확률/습도/풍속/5일 예보/주소) is dummy — the backend
/// doesn't expose them yet. Confirmed 2026-07-14: build the full Figma UI now with dummy values and
/// swap to real data once the backend adds the fields (see home backend-conflicts C-1).
struct WeatherDetailView: View {
    let state: HomeSectionState<(weather: CurrentWeather, detail: WeatherDetail)>
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
        case let .loaded(value):
            VStack(alignment: .leading, spacing: Spacing.lg) {
                todaySection(value.weather, value.detail)
                tipBanner
                detailGrid(value.detail)
                Divider().foregroundStyle(Color.Border.subtle)
                weeklyForecast(value.detail.forecast)
            }
        case let .failed(message):
            Text(message)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .frame(maxWidth: .infinity, minHeight: 300, alignment: .center)
        }
    }

    private func todaySection(_ weather: CurrentWeather, _ detail: WeatherDetail) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.md) {
                AppIconView(source: .asset(WeatherIconMapping.assetName(for: weather.condition)), size: 96, renderingMode: .original)
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(weather.temperature)°")
                        .appTypography(.headlineLargeEmphasized)
                        .foregroundStyle(Color.Text.default)
                    HStack(spacing: 4) {
                        Text("체감 \(detail.feelsLike)°")
                        Text("|")
                        Text("최저 \(detail.lowTemperature)° - 최고 \(detail.highTemperature)°")
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
            detailCard(label: "강수확률", value: "\(detail.precipitationChancePercent)%")
            detailCard(label: "습도", value: "\(detail.humidityPercent)%")
            detailCard(label: "풍속", value: "\(detail.windSpeedMps)m/s")
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
                        AppIconView(source: .asset(day.conditionAssetName), size: 40, renderingMode: .original)
                        Text("\(day.temperature)°")
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
}
