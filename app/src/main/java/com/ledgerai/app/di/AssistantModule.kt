package com.ledgerai.app.di

import com.google.gson.Gson
import com.ledgerai.app.data.ai.AiProviderRouter
import com.ledgerai.app.data.assistant.AuditSink
import com.ledgerai.app.data.assistant.Executor
import com.ledgerai.app.data.assistant.Orchestrator
import com.ledgerai.app.data.assistant.PlanModel
import com.ledgerai.app.data.assistant.RoomAuditSink
import com.ledgerai.app.data.assistant.asPlanModel
import com.ledgerai.app.data.local.room.AssistantAuditDao
import com.ledgerai.app.domain.assistant.ActionRegistry
import com.ledgerai.app.domain.assistant.ActionSpec
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/**
 * Assistant spine. Feature packages add [ActionSpec]s with `@IntoSet`.
 * An empty set is valid: unknown types are rejected by [Executor].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActionSpecBindings {
    @Multibinds
    abstract fun actionSpecs(): Set<ActionSpec>

    @dagger.Binds
    abstract fun assistantFacts(impl: com.ledgerai.app.data.assistant.AssistantContext): com.ledgerai.app.data.assistant.AssistantFacts
}

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    @Singleton
    fun provideActionRegistry(
        specs: Set<@JvmSuppressWildcards ActionSpec>,
    ): ActionRegistry = ActionRegistry(specs.toList())

    /** Capture actions: filing a photo into wardrobe, pantry, receipts, people. */
    @Provides
    @dagger.multibindings.ElementsIntoSet
    fun provideCaptureSpecs(actions: com.ledgerai.app.data.capture.CaptureActions): Set<ActionSpec> =
        actions.specs().toSet()

    /** Clip filing. Risk lives on the spec, and money actions stay high. */
    @Provides
    @dagger.multibindings.ElementsIntoSet
    fun provideClipSpecs(actions: com.ledgerai.app.data.clips.ClipActions): Set<ActionSpec> =
        actions.specs().toSet()

    /** Library add, edit and delete, for the section AI bars and the Home chat. */
    @Provides
    @dagger.multibindings.ElementsIntoSet
    fun provideLibrarySpecs(actions: com.ledgerai.app.data.assistant.LibraryActions): Set<ActionSpec> =
        actions.specs().toSet()

    @Provides
    @Singleton
    fun providePlanModel(router: AiProviderRouter): PlanModel = router.asPlanModel()

    @Provides
    @Singleton
    fun provideOrchestrator(model: PlanModel, registry: ActionRegistry): Orchestrator =
        Orchestrator(model, registry)

    @Provides
    @Singleton
    fun provideLibraryAssistant(
        executor: Executor,
        registry: ActionRegistry,
        facts: com.ledgerai.app.data.assistant.AssistantFacts,
        session: com.ledgerai.app.data.preferences.UserSession,
        gateway: com.ledgerai.app.data.capture.AiGateway,
    ): com.ledgerai.app.data.assistant.LibraryAssistant =
        // Through the gateway, not the bare router: release builds have no model key and must use ai-proxy.
        com.ledgerai.app.data.assistant.LibraryAssistant(
            Orchestrator(PlanModel { system, user -> gateway.fast(system, user) }, registry),
            executor, registry, facts,
        ) { session.userInfo.first().userId }

    @Provides
    @Singleton
    fun provideAuditSink(dao: AssistantAuditDao, gson: Gson): AuditSink = RoomAuditSink(dao, gson)

    @Provides
    @Singleton
    fun provideExecutor(registry: ActionRegistry, audit: AuditSink): Executor =
        Executor(registry, audit)
}
