package com.godsmove.domain.farm

import com.godsmove.domain.common.BaseTimeEntity
import com.godsmove.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "farm")
class Farm(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_member_id", nullable = false)
    val owner: Member,

    @Column(nullable = false, length = 128)
    val name: String,

    @Column(nullable = false, length = 128)
    val region: String,

    @Column(nullable = false, length = 128)
    val city: String,

    @Column(nullable = false, length = 255)
    val street: String,
) : BaseTimeEntity()
