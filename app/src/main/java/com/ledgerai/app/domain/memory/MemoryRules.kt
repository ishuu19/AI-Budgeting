package com.ledgerai.app.domain.memory

sealed class MemoryCheck {
    data object Accepted : MemoryCheck()
    data class Rejected(val reason: String) : MemoryCheck()
}

object MemoryRules {
    /**
     * False because `docs/life-os/data-model.md` does not mark a person link nullable,
     * and `016_people_memory.sql` is a stub that does not allow an unattached memory.
     */
    const val allowsMemoryWithoutPerson: Boolean = false

    fun validate(draft: MemoryDraft): MemoryCheck {
        if (!allowsMemoryWithoutPerson && draft.personId.isNullOrBlank()) {
            return MemoryCheck.Rejected("A memory must point at a person")
        }
        if (draft.text.isBlank()) {
            return MemoryCheck.Rejected("Memory text is required")
        }
        if (draft.sourceType.isBlank()) {
            return MemoryCheck.Rejected("Source type is required")
        }
        return MemoryCheck.Accepted
    }
}
