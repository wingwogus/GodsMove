//
//  HomeViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

/// Per-section load state. Each home section (weather/records/policy/community) fails and empties
/// independently, matching the Figma-implementation rule that every section needs its own loading/
/// empty/error handling rather than one screen-wide state.
enum HomeSectionState<Value: Sendable>: Sendable {
    case loading
    case loaded(Value)
    case failed(String)
}

@MainActor
@Observable
final class HomeViewModel {
    private let recordRepository: RecordRepository
    private let communityRepository: CommunityRepository
    private let policyRepository: PolicyRepository

    private(set) var weatherState: HomeSectionState<(weather: CurrentWeather, detail: WeatherDetail)> = .loading
    private(set) var recentRecordsState: HomeSectionState<[FarmingRecordSummary]> = .loading
    private(set) var policyState: HomeSectionState<PolicyRecommendation?> = .loading
    private(set) var popularPostsState: HomeSectionState<[CommunityPostSummary]> = .loading

    private var didLoad = false

    init(
        recordRepository: RecordRepository,
        communityRepository: CommunityRepository,
        policyRepository: PolicyRepository
    ) {
        self.recordRepository = recordRepository
        self.communityRepository = communityRepository
        self.policyRepository = policyRepository
    }

    /// Loads once per tab lifetime, matching Record/Community's `posts.isEmpty` re-entry guard —
    /// re-navigating to the Home tab doesn't refetch; pull-to-refresh (`reload()`) does.
    func onAppear() async {
        guard !didLoad else { return }
        didLoad = true
        await reload()
    }

    func reload() async {
        async let weatherLoad: Void = loadWeather()
        async let recordsLoad: Void = loadRecentRecords()
        async let policyLoad: Void = loadRecommendedPolicy()
        async let postsLoad: Void = loadPopularPosts()
        _ = await (weatherLoad, recordsLoad, policyLoad, postsLoad)
    }

    private func loadWeather() async {
        weatherState = .loading
        do {
            let farms = try await recordRepository.fetchFarmCrops()
            guard let farmId = farms.first?.farmId else {
                weatherState = .failed("등록된 농장이 없어요")
                return
            }
            let weather = try await recordRepository.fetchWeather(farmId: farmId)
            let detail = WeatherDetail.dummy(temperature: weather.temperature, condition: weather.condition)
            weatherState = .loaded((weather, detail))
        } catch {
            weatherState = .failed(HomeErrorMessage.text(for: error))
        }
    }

    private func loadRecentRecords() async {
        recentRecordsState = .loading
        do {
            let page = try await recordRepository.fetchRecords(RecordQuery(size: 3))
            recentRecordsState = .loaded(page.items)
        } catch {
            recentRecordsState = .failed(HomeErrorMessage.text(for: error))
        }
    }

    private func loadRecommendedPolicy() async {
        policyState = .loading
        do {
            let page = try await policyRepository.fetchRecommendations(PolicyRecommendationQuery(size: 1))
            policyState = .loaded(page.items.first)
        } catch {
            policyState = .failed(HomeErrorMessage.text(for: error))
        }
    }

    private func loadPopularPosts() async {
        popularPostsState = .loading
        do {
            let page = try await communityRepository.fetchPosts(CommunityPostQuery(sort: .popular, size: 3))
            popularPostsState = .loaded(page.items)
        } catch {
            popularPostsState = .failed(HomeErrorMessage.text(for: error))
        }
    }
}
