package com.dokpamine.record

import com.dokpamine.common.ApiException
import com.dokpamine.config.userId
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class RecordController(private val service: RecordService) {

    @PutMapping("/books/{id}")
    fun putBook(auth: Authentication, @PathVariable id: UUID, @Valid @RequestBody dto: BookDto): BookDto =
        service.upsertBook(auth.userId(), dto.also { requireSameId(id, it.id) })

    @PutMapping("/sessions/{id}")
    fun putSession(auth: Authentication, @PathVariable id: UUID, @Valid @RequestBody dto: SessionDto): SessionDto =
        service.upsertSession(auth.userId(), dto.also { requireSameId(id, it.id) })

    @PutMapping("/reviews/{id}")
    fun putReview(auth: Authentication, @PathVariable id: UUID, @Valid @RequestBody dto: ReviewDto): ReviewDto =
        service.upsertReview(auth.userId(), dto.also { requireSameId(id, it.id) })

    @PutMapping("/quotes/{id}")
    fun putQuote(auth: Authentication, @PathVariable id: UUID, @Valid @RequestBody dto: QuoteDto): QuoteDto =
        service.upsertQuote(auth.userId(), dto.also { requireSameId(id, it.id) })

    /** 새 기기에서 로그인했을 때 내 기록 전체를 내려받는다. */
    @GetMapping("/sync/snapshot")
    fun snapshot(auth: Authentication): SnapshotResponse = service.snapshot(auth.userId())

    private fun requireSameId(pathId: UUID, bodyId: UUID) {
        if (pathId != bodyId) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.")
    }
}
