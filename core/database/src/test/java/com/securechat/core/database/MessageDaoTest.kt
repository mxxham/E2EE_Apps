package com.securechat.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.securechat.core.database.dao.MessageDao
import com.securechat.core.database.entity.MessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MessageDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.buildInMemory(ctx)
        dao = db.messageDao()
    }

    @After
    fun tearDown() = db.close()

    private fun message(
        id: String = "msg-1",
        convId: String = "conv-abc",
        body: String = "Hello",
        isMine: Boolean = true,
        timestamp: Long = 1_000L,
        status: String = "PENDING",
    ) = MessageEntity(
        id = id,
        conversationId = convId,
        senderId = if (isMine) "me" else "peer",
        body = body,
        isMine = isMine,
        timestamp = timestamp,
        deliveryStatus = status,
    )

    @Test
    fun `insert and observe returns message`() = runTest {
        dao.upsert(message())
        val list = dao.observeMessages("conv-abc").first()
        assertEquals(1, list.size)
        assertEquals("Hello", list[0].body)
    }

    @Test
    fun `upsert replaces existing message`() = runTest {
        dao.upsert(message())
        dao.upsert(message(body = "Updated"))
        val list = dao.observeMessages("conv-abc").first()
        assertEquals(1, list.size)
        assertEquals("Updated", list[0].body)
    }

    @Test
    fun `updateDeliveryStatus changes status`() = runTest {
        dao.upsert(message(status = "PENDING"))
        dao.updateDeliveryStatus("msg-1", "READ")
        val entity = dao.getById("msg-1")
        assertEquals("READ", entity?.deliveryStatus)
    }

    @Test
    fun `softDelete clears body and sets type to DELETED`() = runTest {
        dao.upsert(message())
        dao.softDelete("msg-1")
        val entity = dao.getById("msg-1")
        assertNotNull(entity)
        assertEquals("DELETED", entity!!.type)
        assertEquals("", entity.body)
    }

    @Test
    fun `getMessagesBefore paginates correctly`() = runTest {
        val messages = (1..10).map { i ->
            message(id = "msg-$i", timestamp = i.toLong() * 1000)
        }
        dao.upsertAll(messages)
        val page = dao.getMessagesBefore("conv-abc", beforeTimestamp = 6_000L, pageSize = 3)
        // Should return messages 5, 4, 3 (descending, before ts 6000)
        assertEquals(3, page.size)
        assertEquals("msg-5", page[0].id)
        assertEquals("msg-4", page[1].id)
        assertEquals("msg-3", page[2].id)
    }

    @Test
    fun `observeUnreadCount counts only non-mine unread`() = runTest {
        dao.upsert(message(id = "m1", isMine = false, status = "DELIVERED"))
        dao.upsert(message(id = "m2", isMine = false, status = "DELIVERED"))
        dao.upsert(message(id = "m3", isMine = true, status = "SENT"))  // mine — excluded
        val unread = dao.observeUnreadCount("conv-abc").first()
        assertEquals(2, unread)
    }

    @Test
    fun `getById returns null for missing message`() = runTest {
        assertNull(dao.getById("nonexistent"))
    }
}
