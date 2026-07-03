//
//  AppTextField.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct AppTextField: View {
    var label: String?
    let placeholder: String
    @Binding var text: String
    var keyboardType: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            if let label {
                Text(label)
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextSecondary)
            }
            TextField(placeholder, text: $text)
                .keyboardType(keyboardType)
                .padding(Spacing.md)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

#Preview {
    AppTextField(label: "이름", placeholder: "이름(실명)을 입력하세요", text: .constant(""))
        .padding()
}
