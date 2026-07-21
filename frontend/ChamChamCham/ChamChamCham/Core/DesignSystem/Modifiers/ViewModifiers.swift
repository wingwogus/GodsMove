//
//  ViewModifiers.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI
import UIKit

private struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(Spacing.md)
            .background(Color.appBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

extension View {
    func cardStyle() -> some View {
        modifier(CardStyle())
    }

    /// Resigns the active text field/editor's first-responder status when the user taps
    /// anywhere else on this view. SwiftUI has no built-in "tap outside to dismiss keyboard"
    /// modifier, so this covers screens where the keyboard would otherwise stay up when the
    /// user taps a non-interactive area instead of scrolling.
    func dismissKeyboardOnTap() -> some View {
        onTapGesture {
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
            )
        }
    }

    func filterRowOverlay(isVisible: Bool, height: CGFloat = 60) -> some View {
        modifier(FilterRowOverlayModifier(isVisible: isVisible, height: height))
    }

}

private struct FilterRowOverlayModifier: ViewModifier {
    let isVisible: Bool
    let height: CGFloat

    func body(content: Content) -> some View {
        content
            .frame(height: height)
            .opacity(isVisible ? 1 : 0)
            .offset(y: isVisible ? 0 : -height)
            .allowsHitTesting(isVisible)
    }
}

struct FilterRowPanObserver: UIViewRepresentable {
    @Binding var isVisible: Bool
    let threshold: CGFloat

    init(isVisible: Binding<Bool>, threshold: CGFloat = 12) {
        _isVisible = isVisible
        self.threshold = threshold
    }

    func makeUIView(context: Context) -> PanObservationView {
        let view = PanObservationView()
        view.onPan = context.coordinator.handlePan(translationY:state:)
        return view
    }

    func updateUIView(_ uiView: PanObservationView, context: Context) {
        context.coordinator.isVisible = $isVisible
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(isVisible: $isVisible, threshold: threshold)
    }

    final class Coordinator {
        var isVisible: Binding<Bool>
        let threshold: CGFloat
        private var lastTranslationY: CGFloat?
        private var accumulatedTranslationY: CGFloat = 0

        init(isVisible: Binding<Bool>, threshold: CGFloat) {
            self.isVisible = isVisible
            self.threshold = threshold
        }

        func handlePan(translationY: CGFloat, state: UIGestureRecognizer.State) {
            switch state {
            case .began:
                lastTranslationY = translationY
                accumulatedTranslationY = 0
            case .changed:
                guard let lastTranslationY else {
                    self.lastTranslationY = translationY
                    return
                }

                let delta = translationY - lastTranslationY
                if (delta > 0 && accumulatedTranslationY < 0) || (delta < 0 && accumulatedTranslationY > 0) {
                    accumulatedTranslationY = 0
                }
                accumulatedTranslationY += delta
                self.lastTranslationY = translationY

                if accumulatedTranslationY <= -threshold {
                    setVisible(false)
                } else if accumulatedTranslationY >= threshold {
                    setVisible(true)
                }
            case .cancelled, .ended, .failed:
                lastTranslationY = nil
                accumulatedTranslationY = 0
            default:
                break
            }
        }

        private func setVisible(_ newValue: Bool) {
            guard isVisible.wrappedValue != newValue else { return }
            accumulatedTranslationY = 0
            withAnimation(.easeInOut(duration: 0.18)) {
                isVisible.wrappedValue = newValue
            }
        }
    }
}

final class PanObservationView: UIView {
    var onPan: ((CGFloat, UIGestureRecognizer.State) -> Void)?

    private weak var observedScrollView: UIScrollView?
    private var isLookupScheduled = false

    override func didMoveToSuperview() {
        super.didMoveToSuperview()
        observeAncestorScrollView()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        observeAncestorScrollView()
    }

    deinit {
        observedScrollView?.panGestureRecognizer.removeTarget(self, action: #selector(handlePan(_:)))
    }

    private func observeAncestorScrollView() {
        guard observedScrollView == nil else { return }
        guard let scrollView = ancestorScrollView() else {
            scheduleLookup()
            return
        }

        observedScrollView = scrollView
        scrollView.panGestureRecognizer.addTarget(self, action: #selector(handlePan(_:)))
    }

    private func scheduleLookup() {
        guard !isLookupScheduled else { return }
        isLookupScheduled = true
        DispatchQueue.main.async { [weak self] in
            self?.isLookupScheduled = false
            self?.observeAncestorScrollView()
        }
    }

    private func ancestorScrollView() -> UIScrollView? {
        var view = superview
        while let currentView = view {
            if let scrollView = currentView as? UIScrollView {
                return scrollView
            }
            view = currentView.superview
        }
        return nil
    }

    @objc private func handlePan(_ gestureRecognizer: UIPanGestureRecognizer) {
        onPan?(gestureRecognizer.translation(in: observedScrollView).y, gestureRecognizer.state)
    }
}

// MARK: - Fixed bottom CTA + keyboard pattern
//
// Screens with a text field/editor and a fixed bottom CTA (`.safeAreaInset(edge: .bottom)` or
// `.overlay(alignment: .bottom)`) must apply `.ignoresSafeArea(.keyboard, edges: .bottom)` as
// the LAST modifier in the chain — after the modifier that adds the bottom CTA, not before it
// and not nested inside the CTA's own content closure. Applying it earlier, or only to the CTA
// view itself, does not stop the ancestor view from avoiding the keyboard, so the CTA still
// rides up and sticks to the keyboard instead of staying pinned to the screen bottom while the
// keyboard covers it.
//
//     VStack { ... }
//         .safeAreaInset(edge: .bottom) { bottomCTA }   // or .overlay(alignment: .bottom) { ... }
//         .ignoresSafeArea(.keyboard, edges: .bottom)   // must come after, wraps the whole chain
//
// Separately, any keyboard without a Return/완료 key (`.numberPad`, `.phonePad`, etc.) should get
// an explicit dismiss button via the keyboard accessory toolbar, since tap-outside/scroll-to-dismiss
// alone feels unresponsive for those fields:
//
//     .toolbar {
//         ToolbarItemGroup(placement: .keyboard) {
//             Spacer()
//             Button("완료") {
//                 UIApplication.shared.sendAction(
//                     #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
//                 )
//             }
//         }
//     }
