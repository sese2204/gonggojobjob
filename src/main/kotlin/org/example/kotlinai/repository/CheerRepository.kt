package org.example.kotlinai.repository

import org.example.kotlinai.entity.Cheer
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CheerRepository : JpaRepository<Cheer, Long> {

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Cheer>
}
