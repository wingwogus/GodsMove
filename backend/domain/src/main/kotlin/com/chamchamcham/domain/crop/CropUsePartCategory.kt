package com.chamchamcham.domain.crop

enum class CropUsePartCategory(val label: String) {
    WHOLE_HERB("전초"),
    ROOT_BARK("뿌리·껍질"),
    RHIZOME("뿌리줄기"),
    LEAF("잎"),
    FLOWER("꽃"),
    FRUIT("열매/과실"),
    SEED("종자"),
    STEM_BRANCH("줄기/가지"),
    UNKNOWN("기타")
}
