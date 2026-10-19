package com.aiphoneagent.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [ScenarioRepositoryImpl] using an in-memory fake of [ScenarioDao].
 * Verifies create/observe/delete behaviour and the entity -> domain mapping without
 * needing a real Room database.
 */
class ScenarioRepositoryTest {

    /** Minimal in-memory implementation of [ScenarioDao] for tests. */
    private class FakeScenarioDao : ScenarioDao {
        private val store = MutableStateFlow<List<SavedScenarioEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(entity: SavedScenarioEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            val saved = entity.copy(id = id)
            store.value = store.value + saved
            return id
        }

        override fun observeAll(): Flow<List<SavedScenarioEntity>> =
            store.map { list -> list.sortedByDescending { it.createdAt } }

        override suspend fun delete(id: Long) {
            store.value = store.value.filterNot { it.id == id }
        }
    }

    private fun repo() = ScenarioRepositoryImpl(FakeScenarioDao())

    @Test fun createReturnsIdAndPersists() = runTest {
        val repo = repo()
        val id = repo.create(name = "Send hello", prompt = "Send 'hello' to mom on WhatsApp")
        assertThat(id).isEqualTo(1L)

        repo.observeAll().test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            val scenario = list.first()
            assertThat(scenario.id).isEqualTo(1L)
            assertThat(scenario.name).isEqualTo("Send hello")
            assertThat(scenario.prompt).contains("WhatsApp")
            assertThat(scenario.paramsJson).isEqualTo("{}")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun createTrimsWhitespace() = runTest {
        val repo = repo()
        repo.create(name = "  Trimmed  ", prompt = "  do it  ")
        repo.observeAll().test {
            val scenario = awaitItem().first()
            assertThat(scenario.name).isEqualTo("Trimmed")
            assertThat(scenario.prompt).isEqualTo("do it")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun createStoresParamsJson() = runTest {
        val repo = repo()
        repo.create(name = "n", prompt = "p", paramsJson = """{"contact":"Alex"}""")
        repo.observeAll().test {
            assertThat(awaitItem().first().paramsJson).isEqualTo("""{"contact":"Alex"}""")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeAllEmitsNewestFirst() = runTest {
        val repo = repo()
        // Two inserts; the fake preserves insertion order but observeAll sorts by createdAt desc.
        repo.create("first", "p1")
        Thread.sleep(2)
        repo.create("second", "p2")
        repo.observeAll().test {
            val list = awaitItem()
            assertThat(list.map { it.name }).containsExactly("second", "first")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun deleteRemovesScenario() = runTest {
        val repo = repo()
        val id = repo.create("temp", "p")
        repo.delete(id)
        repo.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun deleteUnknownIdIsNoOp() = runTest {
        val repo = repo()
        repo.create("keep", "p")
        repo.delete(999L)
        repo.observeAll().test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
