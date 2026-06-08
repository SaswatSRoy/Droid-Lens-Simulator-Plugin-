package com.droidlens.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droidlens.db.entities.BaselineSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineDaoTest {
    private lateinit var database: DroidLensDatabase
    private lateinit var baselineDao: BaselineDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DroidLensDatabase::class.java).build()
        baselineDao = database.baselineDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetLatestBaseline() = runBlocking {
        val snapshot = BaselineSnapshot(
            sessionName = "test-session",
            capturedAtMs = 1000L,
            avgFrameTimeMs = 16.6f,
            p95FrameTimeMs = 20.0f,
            jankFrameCount = 5,
            composableRecompositions = "{}",
            usedMemoryMb = 50.0f,
            maxMemoryMb = 512.0f,
            startupTimeMs = 200L,
            appVersion = "1.0"
        )

        baselineDao.insertBaseline(snapshot)
        
        val latest = baselineDao.getLatestBaseline("test-session")
        assertNotNull(latest)
        assertEquals("test-session", latest?.sessionName)
        assertEquals(16.6f, latest?.avgFrameTimeMs)
    }
}
