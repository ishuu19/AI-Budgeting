package com.ledgerai.app.di

import android.content.Context
import com.ledgerai.app.data.ai.QuickParse
import com.ledgerai.app.data.ai.RuleLexicon
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.concurrent.thread

@Module
@InstallIn(SingletonComponent::class)
object RulesModule {

    /** Phrase tables from `assets/rules`. Parsed once, on a background thread, before the first parse needs them. */
    @Provides
    @Singleton
    fun ruleLexicon(@ApplicationContext context: Context): RuleLexicon {
        val lexicon = RuleLexicon.fromStreams { name -> runCatching { context.assets.open("rules/$name") }.getOrNull() }
        QuickParse.lexicon = lexicon
        thread(name = "rule-lexicon", isDaemon = true) { lexicon.isEmpty }
        return lexicon
    }
}
