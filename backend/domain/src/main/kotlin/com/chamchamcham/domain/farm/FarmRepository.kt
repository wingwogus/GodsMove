package com.chamchamcham.domain.farm

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FarmRepository : JpaRepository<Farm, UUID> {
    fun findByOwnerId(ownerId: UUID): List<Farm>

    @Query(
        """
        select distinct f
        from Farm f
        left join fetch f.boundaryCoordinates
        where f.owner.id = :ownerId
        """
    )
    fun findAllWithBoundaryCoordinatesByOwnerId(
        @Param("ownerId") ownerId: UUID
    ): List<Farm>

    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Farm?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select f from Farm f
        where f.id = :farmId
          and f.owner.id = :memberId
        """,
    )
    fun findOwnedByIdForReportUpdate(
        farmId: UUID,
        memberId: UUID,
    ): Farm?

    fun findFirstByOwnerIdOrderByCreatedAtAsc(ownerId: UUID): Farm?
}
