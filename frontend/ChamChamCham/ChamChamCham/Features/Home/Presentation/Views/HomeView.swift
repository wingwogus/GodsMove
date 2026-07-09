//
//  HomeView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Layout-only wireframe for the home tab. No navigation to detail screens yet —
/// buttons and section chevrons are inert placeholders until those screens exist.
struct HomeView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                header
                searchField
                tipBanner
                weatherForecastCard
                recentFarmingRecordSection
                recommendedPolicySection
                popularPostsSection
            }
            .padding(Spacing.md)
        }
    }

    private var header: some View {
        HStack {
            Text("홈")
                .font(.largeTitle.bold())
            Spacer()
            Button {
            } label: {
                Image(systemName: "bell")
                    .font(.title2)
            }
        }
    }

    private var searchField: some View {
        HStack {
            Text("작물에 대해 궁금한 점을 검색해보세요.")
                .foregroundStyle(.secondary)
            Spacer()
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
        }
        .padding(Spacing.md)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var tipBanner: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Text("팁")
                .font(.caption.bold())
                .padding(.horizontal, Spacing.sm)
                .padding(.vertical, 4)
                .background(Color(.tertiarySystemBackground))
                .clipShape(Capsule())
            Text("최근 관수 간격이 평균 3일로 짧아요.\n겉흙이 마른 뒤 주는 게 더 좋습니다!")
                .font(.subheadline)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var weatherForecastCard: some View {
        VStack(spacing: Spacing.sm) {
            HStack {
                ForEach(weatherSampleData) { day in
                    VStack(spacing: Spacing.xs) {
                        Text(day.label)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Image(systemName: day.symbolName)
                            .font(.title2)
                        Text(day.temperature)
                            .font(.subheadline)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            Divider()
            HStack(spacing: Spacing.xs) {
                Image(systemName: "lightbulb")
                Text("내일 비 예보. 방제는 오늘 끝내는 걸 추천해요.")
                    .font(.footnote)
                Spacer()
            }
        }
        .padding(Spacing.md)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var recentFarmingRecordSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            sectionHeader("나의 최근 영농 기록")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(farmingRecordSampleData) { record in
                        FarmingRecordCard(record: record)
                    }
                }
            }
            Button {
            } label: {
                Text("새로 작성하기 +")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.sm)
            }
            .buttonStyle(.bordered)
            .clipShape(Capsule())
        }
    }

    private var recommendedPolicySection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            sectionHeader("오늘의 추천 정책")
            HStack {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("D-12")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("청년농 영농정착 지원금")
                        .font(.headline)
                }
                Spacer()
                Image(systemName: "arrow.right.circle.fill")
                    .font(.title)
            }
            .padding(Spacing.md)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private var popularPostsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            sectionHeader("게시판 인기글")
            VStack(spacing: 0) {
                ForEach(popularPostSampleData) { post in
                    PopularPostRow(post: post)
                    if post.id != popularPostSampleData.last?.id {
                        Divider()
                    }
                }
            }
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.headline)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}

private struct WeatherDaySample: Identifiable {
    let id = UUID()
    let label: String
    let symbolName: String
    let temperature: String
}

private let weatherSampleData: [WeatherDaySample] = [
    .init(label: "오늘", symbolName: "sun.max", temperature: "23°"),
    .init(label: "내일", symbolName: "cloud.rain", temperature: "19°"),
    .init(label: "모레", symbolName: "cloud", temperature: "21°"),
]

private struct FarmingRecordSample: Identifiable {
    let id = UUID()
    let cropName: String
    let title: String
    let subtitle: String
}

private let farmingRecordSampleData: [FarmingRecordSample] = [
    .init(cropName: "인삼", title: "비료주기", subtitle: "1번 농지 | **비료 사용"),
    .init(cropName: "도라지", title: "비료주기", subtitle: "1번 농지 | **비료 사용"),
]

private struct FarmingRecordCard: View {
    let record: FarmingRecordSample

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(.secondarySystemBackground))
                    .frame(width: 160, height: 100)
                Text(record.cropName)
                    .font(.caption)
                    .padding(.horizontal, Spacing.sm)
                    .padding(.vertical, 4)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(Capsule())
                    .padding(Spacing.sm)
            }
            Text(record.title)
                .font(.subheadline.bold())
            Text(record.subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(width: 160, alignment: .leading)
    }
}

private struct PopularPostSample: Identifiable {
    let id = UUID()
    let tag: String
    let preview: String
}

private let popularPostSampleData: [PopularPostSample] = [
    .init(tag: "팁 공유", preview: "저는 병충해 이렇게 해결했는데 오늘 여러분..."),
    .init(tag: "작업 자랑", preview: "저는 병충해 이렇게 해결했는데 오늘 여러분..."),
    .init(tag: "질문", preview: "저는 병충해 이렇게 해결했는데 오늘 여러분..."),
]

private struct PopularPostRow: View {
    let post: PopularPostSample

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(post.tag)
                .font(.caption)
                .padding(.horizontal, Spacing.sm)
                .padding(.vertical, 4)
                .background(Color(.secondarySystemBackground))
                .clipShape(Capsule())
            Text(post.preview)
                .font(.subheadline)
        }
        .padding(.vertical, Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    HomeView()
}
