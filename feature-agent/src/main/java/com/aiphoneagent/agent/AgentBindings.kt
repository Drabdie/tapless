package com.aiphoneagent.agent

import com.aiphoneagent.common.ActionExecutor
import com.aiphoneagent.common.ActionLogRepository
import com.aiphoneagent.common.AgentRunner
import com.aiphoneagent.common.DefaultKillSwitch
import com.aiphoneagent.common.KillSwitch
import com.aiphoneagent.common.Planner
import com.aiphoneagent.common.RecoveryEngine
import com.aiphoneagent.common.RuleBasedPlanner
import com.aiphoneagent.common.ScreenObserver
import com.aiphoneagent.common.TaskContextProvider
import com.aiphoneagent.common.TaskParser
import com.aiphoneagent.common.Validator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentBindings {
    @Provides @Singleton fun parser(): TaskParser = TaskParser()
    @Provides @Singleton fun rules(): RuleBasedPlanner = RuleBasedPlanner()
    @Provides @Singleton fun validator(): Validator = Validator()
    @Provides @Singleton fun recovery(): RecoveryEngine = RecoveryEngine()
    @Provides @Singleton fun killSwitch(): KillSwitch = DefaultKillSwitch()
    @Provides @Singleton fun taskContext(): TaskContextProvider = TaskContextProvider()

    @Provides
    @Singleton
    fun planner(impl: HybridPlanner): Planner = impl

    @Provides
    @Singleton
    fun runner(
        parser: TaskParser,
        planner: Planner,
        executor: ActionExecutor,
        observer: ScreenObserver,
        validator: Validator,
        recovery: RecoveryEngine,
        killSwitch: KillSwitch,
        logs: ActionLogRepository,
        taskContext: TaskContextProvider,
    ): AgentRunner = AgentRunner(
        parser, planner, executor, observer, validator, recovery, killSwitch, logs, taskContext,
    )
}
