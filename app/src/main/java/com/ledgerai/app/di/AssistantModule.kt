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
}

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    @Singleton
    fun provideActionRegistry(
        specs: Set<@JvmSuppressWildcards ActionSpec>,
    ): ActionRegistry = ActionRegistry(specs.toList())

    @Provides
    @Singleton
    fun providePlanModel(router: AiProviderRouter): PlanModel = router.asPlanModel()

    @Provides
    @Singleton
    fun provideOrchestrator(model: PlanModel, registry: ActionRegistry): Orchestrator =
        Orchestrator(model, registry)

    @Provides
    @Singleton
    fun provideAuditSink(dao: AssistantAuditDao, gson: Gson): AuditSink = RoomAuditSink(dao, gson)

    @Provides
    @Singleton
    fun provideExecutor(registry: ActionRegistry, audit: AuditSink): Executor =
        Executor(registry, audit)
}
