package vn.htq.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import vn.htq.core.*

@RunWith(AndroidJUnit4::class)
class FoundationTest {
    @Test fun bundledCatalogAndPersistentRewardTransaction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = Catalog.load(object : AssetSource {
            override fun open(relative: String) = context.assets.open("htq/" + canonicalPath(relative))
        })
        assertEquals(3897, catalog.assets.size)
        assertEquals(660, catalog.taskCount)
        assertEquals(816, catalog.choiceCount)
        assertEquals(544, catalog.gradedCount)
        assertEquals(116, catalog.openCount)
        val evaluator = AssessmentEngine(catalog)
        val ticket = catalog.activities.values.flatMap { it.taskIds }
            .map { evaluator.evaluate(it, Response.ParticipationDone, true) }
            .filterIsInstance<Verdict.Completed>().first().ticket
        // This test runs in an isolated CI emulator, not on a user's phone.
        context.deleteDatabase("htq_progress.db")
        try {
            val store = LocalProgress(context, catalog)
            assertTrue(store.commit(ticket))
            assertFalse(store.commit(ticket))
            assertEquals(1, store.completedCount())
            store.saveSession(SessionPolicy(catalog).snapshotForTask(ticket.taskId))
            store.close()
            val restored = LocalProgress(context, catalog)
            assertEquals(ticket.taskId, restored.loadSession()!!.s("task_id"))
            assertTrue(restored.claim(ticket.taskId))
            assertFalse(restored.claim(ticket.taskId))
            assertFalse(restored.commit(ticket))
            assertEquals(1, restored.inventory().values.sum())
            restored.close()
            val again = LocalProgress(context, catalog)
            assertFalse(again.claim(ticket.taskId))
            assertEquals(1, again.inventory().values.sum())
            again.close()
        } finally { context.deleteDatabase("htq_progress.db") }
    }
}
