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
        val policy=RewardPolicy(catalog)
        val pair=catalog.activities.values.flatMap{it.taskIds}
            .map{evaluator.evaluate(it,Response.ParticipationDone,true)}
            .filterIsInstance<Verdict.Completed>().map{it.ticket}
            .groupBy{policy.entitlement(it).itemId}.values.first{it.size>=2}.take(2)
        val ticket=pair[0];val second=pair[1]
        val item=policy.entitlement(ticket).itemId
        assertEquals(item,policy.entitlement(second).itemId)
        assertNotEquals(ticket.taskId,second.taskId)
        // This test runs in an isolated CI emulator, not on a user's phone.
        context.deleteDatabase("htq_progress.db")
        try {
            val store = LocalProgress(context, catalog)
            val lesson=catalog.childTask(ticket.taskId).lessonId
            assertTrue(store.openedLessons().isEmpty())
            store.markLessonOpened(lesson);store.markLessonOpened(lesson)
            assertEquals(setOf(lesson),store.openedLessons())
            assertFalse(store.claim(ticket.taskId));assertFalse(store.place(ticket.taskId,0.5,0.5))
            assertTrue(store.commit(ticket));assertTrue(store.commit(second))
            assertFalse(store.commit(ticket));assertFalse(store.commit(second))
            assertEquals(2,store.completedCount())
            assertEquals(setOf(ticket.taskId,second.taskId),store.pendingRewards().map{it.task}.toSet())
            assertTrue(store.inventory().isEmpty());assertTrue(store.gardenEntries().isEmpty())
            assertFalse(store.place(ticket.taskId,0.5,0.5))
            store.saveSession(SessionPolicy(catalog).snapshotForTask(ticket.taskId))
            store.close()
            val restored = LocalProgress(context, catalog)
            assertEquals(ticket.taskId, restored.loadSession()!!.s("task_id"))
            assertTrue(restored.claim(ticket.taskId))
            assertFalse(restored.claim(ticket.taskId))
            assertFalse(restored.commit(ticket))
            assertEquals(listOf(second.taskId),restored.pendingRewards().map{it.task})
            assertEquals(item,restored.claimedReward(ticket.taskId));assertNull(restored.claimedReward(second.taskId))
            assertEquals(1, restored.inventory().values.sum())
            val owned=restored.inventoryBreakdown().single()
            assertEquals(1,owned.inBag);assertEquals(0,owned.placed)
            assertTrue(restored.place(ticket.taskId,0.5,0.5))
            assertEquals(0,restored.inventoryBreakdown().single().inBag)
            assertEquals(1,restored.inventoryBreakdown().single().placed)
            assertTrue(restored.place(ticket.taskId,0.25,0.75))
            assertEquals(1,restored.gardenEntries().size)
            assertEquals(0.25,restored.gardenEntries().single().x!!,0.000001)
            assertEquals(1,restored.inventoryBreakdown().single().placed)
            restored.close()
            val again=LocalProgress(context,catalog)
            assertEquals(listOf(second.taskId),again.pendingRewards().map{it.task})
            assertEquals(0.75,again.gardenEntries().single().y!!,0.000001)
            assertFalse(again.claim(ticket.taskId));assertTrue(again.claim(second.taskId));assertFalse(again.claim(second.taskId))
            assertTrue(again.pendingRewards().isEmpty())
            assertEquals(mapOf(item to 2),again.inventory())
            assertTrue(again.place(second.taskId,0.8,0.2))
            assertEquals(2,again.inventoryBreakdown().single().placed)
            assertTrue(again.putAway(ticket.taskId));assertFalse(again.putAway(ticket.taskId))
            assertEquals(1,again.inventoryBreakdown().single().inBag)
            assertEquals(1,again.inventoryBreakdown().single().placed)
            assertEquals(0.8,again.gardenEntries().single{it.task==second.taskId}.x!!,0.000001)
            assertEquals(2,again.completedCount());assertFalse(again.commit(second))
            again.close()
            val finalStore=LocalProgress(context,catalog)
            assertEquals(mapOf(item to 2),finalStore.inventory())
            assertEquals(1,finalStore.inventoryBreakdown().single().placed)
            assertEquals(1,finalStore.inventoryBreakdown().single().inBag)
            assertTrue(finalStore.pendingRewards().isEmpty())
            val expectedLessons=setOf(catalog.childTask(ticket.taskId).lessonId,catalog.childTask(second.taskId).lessonId)
            assertEquals(expectedLessons,finalStore.openedLessons())
            assertEquals(2,finalStore.recordedCompletions().size)
            // Recreate a v1 schema with the already validated old records, then exercise SQLiteOpenHelper migration.
            finalStore.writableDatabase.execSQL("DROP TABLE opened_lessons")
            finalStore.writableDatabase.version=1
            finalStore.close()
            val migrated=LocalProgress(context,catalog)
            assertEquals(2,migrated.readableDatabase.version)
            assertEquals(expectedLessons,migrated.openedLessons())
            assertEquals(2,migrated.completedCount())
            assertEquals(ticket.taskId,migrated.loadSession()!!.s("task_id"))
            assertEquals(mapOf(item to 2),migrated.inventory())
            assertEquals(1,migrated.inventoryBreakdown().single().placed)
            assertEquals(0.8,migrated.gardenEntries().single{it.task==second.taskId}.x!!,0.000001)
            assertTrue(migrated.pendingRewards().isEmpty())
            migrated.resetProgress()
            assertTrue(migrated.openedLessons().isEmpty());assertTrue(migrated.recordedCompletions().isEmpty())
            assertNull(migrated.loadSession());assertTrue(migrated.inventory().isEmpty())
            assertTrue(migrated.gardenEntries().isEmpty());assertTrue(migrated.pendingRewards().isEmpty())
            migrated.close()
            android.util.Log.i("HTQ_UI_QA","PARENT_STORAGE_PASS opened_dedup=true migration_preserves_v1=true reset_atomic=true")
        } finally { context.deleteDatabase("htq_progress.db") }
    }
}
