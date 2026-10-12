package com.campusverse.app

import com.campusverse.app.data.local.ai.AiEngineMode
import com.campusverse.app.data.local.ai.LocalModelCatalog
import com.campusverse.app.data.local.ai.LocalModelManager
import com.campusverse.app.data.local.ai.ModelDownloadStatus
import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.domain.student.StudentRepository
import com.campusverse.app.ui.screens.student.ai.AiStudyAssistantViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAiTests {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLocalModelCatalog_containsValidModels() {
        val models = LocalModelCatalog.ALL_MODELS
        assertTrue("Catalog must not be empty", models.isNotEmpty())

        val defaultModel = LocalModelCatalog.DEFAULT_MODEL
        assertEquals("smollm2-360m-instruct-q4", defaultModel.id)
        assertTrue(defaultModel.isRecommended)
        assertEquals("GGUF", defaultModel.format)
        assertTrue(defaultModel.expectedSizeBytes > 200_000_000L)
        assertTrue(defaultModel.downloadUrl.startsWith("https://"))

        // Lookup by ID
        val found = LocalModelCatalog.getById("smollm2-360m-instruct-q4")
        assertNotNull(found)
        assertEquals("SmolLM2 360M Instruct", found?.name)

        val unknown = LocalModelCatalog.getById("non_existent_model")
        assertNull(unknown)
    }

    @Test
    fun testLocalModelInfo_displaySize() {
        val model = LocalModelCatalog.SMOL_LM_360M
        assertTrue(model.displaySize.endsWith("MB"))
    }

    @Test
    fun testLocalModelManager_formatBytes() {
        assertEquals("0 MB", LocalModelManager.formatBytes(0))
        assertEquals("100.0 MB", LocalModelManager.formatBytes(100L * 1024 * 1024))
        assertEquals("2.00 GB", LocalModelManager.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun testLocalModelManager_isValidGguf() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "gguf_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Valid GGUF file (first 4 bytes: 0x47, 0x47, 0x55, 0x46)
            val validFile = File(tempDir, "valid.gguf")
            FileOutputStream(validFile).use { fos ->
                fos.write(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x00, 0x01, 0x02, 0x03))
            }
            assertTrue("Valid GGUF header must return true", LocalModelManager.isValidGguf(validFile))

            // Invalid header
            val invalidFile = File(tempDir, "invalid.gguf")
            FileOutputStream(invalidFile).use { fos ->
                fos.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) // ZIP magic bytes
            }
            assertFalse("Non-GGUF header must return false", LocalModelManager.isValidGguf(invalidFile))

            // File too small
            val smallFile = File(tempDir, "small.gguf")
            FileOutputStream(smallFile).use { fos ->
                fos.write(byteArrayOf(0x47, 0x47))
            }
            assertFalse("File with fewer than 4 bytes must return false", LocalModelManager.isValidGguf(smallFile))

            // Non-existent file
            val nonExistent = File(tempDir, "does_not_exist.gguf")
            assertFalse("Non-existent file must return false", LocalModelManager.isValidGguf(nonExistent))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testViewModel_engineModeSwitching() = runTest {
        val vm = AiStudyAssistantViewModel(
            repository = FakeStudentRepository()
        )

        assertEquals(AiEngineMode.AUTO, vm.uiState.value.engineMode)

        vm.setEngineMode(AiEngineMode.LOCAL)
        assertEquals(AiEngineMode.LOCAL, vm.uiState.value.engineMode)

        vm.setEngineMode(AiEngineMode.CLOUD)
        assertEquals(AiEngineMode.CLOUD, vm.uiState.value.engineMode)

        vm.setEngineMode(AiEngineMode.AUTO)
        assertEquals(AiEngineMode.AUTO, vm.uiState.value.engineMode)
    }

    @Test
    fun testViewModel_studyModeSwitching() = runTest {
        val vm = AiStudyAssistantViewModel(
            repository = FakeStudentRepository()
        )

        assertEquals("EXPLAIN", vm.uiState.value.selectedMode)

        vm.setMode("SUMMARIZE")
        assertEquals("SUMMARIZE", vm.uiState.value.selectedMode)

        vm.setMode("CONCEPT_QA")
        assertEquals("CONCEPT_QA", vm.uiState.value.selectedMode)
    }

    @Test
    fun testViewModel_localModeWhenModelNotInstalled_promptsUser() = runTest {
        val vm = AiStudyAssistantViewModel(
            repository = FakeStudentRepository()
        )

        vm.setEngineMode(AiEngineMode.LOCAL)
        vm.submitQuery("Explain Dijkstra Algorithm")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue("Model manager should open when model is not downloaded", state.isModelManagerOpen)

        val lastMessage = state.messages.last()
        assertEquals("AI", lastMessage.sender)
        assertTrue(lastMessage.isErrorOrUnavailable)
        assertTrue(lastMessage.text.contains("Model Manager") || lastMessage.text.contains("not downloaded"))
        assertEquals(AiEngineMode.LOCAL, lastMessage.engine)
    }

    @Test
    fun testViewModel_cloudModeSuccess() = runTest {
        val fakeRepo = FakeStudentRepository()

        val vm = AiStudyAssistantViewModel(repository = fakeRepo)
        vm.setEngineMode(AiEngineMode.CLOUD)
        vm.submitQuery("Explain B-Trees")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        val lastMessage = state.messages.last()
        assertEquals("AI", lastMessage.sender)
        assertTrue(lastMessage.text.contains("Detailed explanation of Explain B-Trees."))
        assertEquals(AiEngineMode.CLOUD, lastMessage.engine)
        assertEquals("Gemini Pro", lastMessage.modelName)
        assertFalse(lastMessage.isErrorOrUnavailable)
    }

    @Test
    fun testViewModel_restartChat_resetsMessages() = runTest {
        val vm = AiStudyAssistantViewModel(repository = FakeStudentRepository())
        vm.submitQuery("Test Query")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.messages.size > 1)

        vm.restartChat()
        assertEquals(1, vm.uiState.value.messages.size)
        assertEquals("AI", vm.uiState.value.messages.first().sender)
    }
}
