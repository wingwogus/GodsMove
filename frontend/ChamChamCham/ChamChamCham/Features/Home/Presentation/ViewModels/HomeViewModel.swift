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
    private let weatherRepository: WeatherRepository

    private(set) var weatherState: HomeSectionState<CurrentWeather> = .loading
    private(set) var recentRecordsState: HomeSectionState<[FarmingRecordSummary]> = .loading
    private(set) var policyState: HomeSectionState<PolicyRecommendation?> = .loading
    private(set) var popularPostsState: HomeSectionState<[CommunityPostSummary]> = .loading

    /// 날씨 상세는 홈 카드와 별개로, 화면에 들어갈 때만 조회한다 — 상세 전용 필드(체감/자외선/습도/풍속/
    /// 5일예보)까지 홈 로드 때마다 같이 부르면 백엔드가 홈/상세를 나눈 목적(불필요한 외부 호출 비용 제거)이
    /// 사라진다.
    private(set) var weatherDetailState: HomeSectionState<WeatherDetail> = .loading
    private var weatherDetailLoaded = false

    private var didLoad = false

    init(
        recordRepository: RecordRepository,
        communityRepository: CommunityRepository,
        policyRepository: PolicyRepository,
        weatherRepository: WeatherRepository
    ) {
        self.recordRepository = recordRepository
        self.communityRepository = communityRepository
        self.policyRepository = policyRepository
        self.weatherRepository = weatherRepository
    }

    /// Loads once per tab lifetime, matching Record/Community's `posts.isEmpty` re-entry guard —
    /// re-navigating to the Home tab doesn't refetch; pull-to-refresh (`reload()`) does.
    func onAppear() async {
        guard !didLoad else { return }
        didLoad = true
        await reload()
    }

    func reload() async {
        weatherDetailLoaded = false // 다음 상세 진입 때 재조회하도록 무효화(상세 화면 자체를 새로고침하진 않음).
        async let weatherLoad: Void = loadWeather()
        async let recordsLoad: Void = loadRecentRecords()
        async let policyLoad: Void = loadRecommendedPolicy()
        async let postsLoad: Void = loadPopularPosts()
        _ = await (weatherLoad, recordsLoad, policyLoad, postsLoad)
    }

    private func loadWeather() async {
        weatherState = .loading
        do {
            // farmId 생략 → 백엔드가 첫 등록 농지로 해석(계획대로), 여기서 별도 농지 조회가 필요 없다.
            weatherState = .loaded(try await weatherRepository.fetchHome(farmId: nil))
        } catch {
            weatherState = .failed(HomeErrorMessage.text(for: error))
        }
    }

    /// `HomeRoute.weatherDetail`로 진입할 때 한 번만 호출. 실패 후 재진입해도 재조회하지 않는 건
    /// (`reload()`가 아니라 여기서만 리셋) 다른 섹션들과 동일한 "한 번 로드, pull-to-refresh로만 재시도"
    /// 규칙을 따른 것.
    func loadWeatherDetailIfNeeded() async {
        guard !weatherDetailLoaded else { return }
        weatherDetailLoaded = true
        weatherDetailState = .loading
        do {
            weatherDetailState = .loaded(try await weatherRepository.fetchDetail(farmId: nil))
        } catch {
            weatherDetailState = .failed(HomeErrorMessage.text(for: error))
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

    /// "나의 게시판 인기글" must scope to boards the member registered under 나의 작물. The `cropId` filter
    /// is a repeated/array query param the backend accepts directly (see `CommunityEndpoint.queryItems`),
    /// so the member's boards are sent server-side — filtering an unscoped page client-side instead would
    /// silently truncate to whatever the top-3 unfiltered popular posts happened to include, with no
    /// retry if none of them matched.
    private func loadPopularPosts() async {
        popularPostsState = .loading
        do {
            let myCropIds = try await communityRepository.fetchBoards().map(\.cropId)
            let page = try await communityRepository.fetchPosts(
                CommunityPostQuery(cropIds: myCropIds, sort: .popular, size: 3)
            )
            popularPostsState = .loaded(page.items)
        } catch {
            popularPostsState = .failed(HomeErrorMessage.text(for: error))
        }
    }
}
