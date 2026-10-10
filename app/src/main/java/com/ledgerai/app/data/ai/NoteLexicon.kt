package com.ledgerai.app.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Tag keywords and nudge trigger phrases: assets/rules/notes_tags.tsv and nudge_triggers.tsv over the embedded defaults. */
@Singleton
class NoteLexicon @Inject constructor(@ApplicationContext private val context: Context) {

    val tags: Map<String, List<String>> by lazy {
        val text = RuleAssets.read(context, "rules/notes_tags.tsv")
        NoteRules.mergeTags(if (text == null) emptyMap() else NoteRules.parseTagTsv(text))
    }

    val triggers: List<String> by lazy {
        val text = RuleAssets.read(context, "rules/nudge_triggers.tsv")
        NoteRules.mergeTriggers(if (text == null) emptyList() else NoteRules.parseTriggerTsv(text))
    }
}
