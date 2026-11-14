package com.gitactionbuild

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileUtilsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun pbarShowsHashesAndPercent() {
        assertEquals("[#####-----] 50%", pbar(50))
        assertEquals("[----------] 0%", pbar(-5))
        assertEquals("[##########] 100%", pbar(250))
    }

    @Test
    fun bar10ShowsBlocksAndPercent() {
        assertEquals("[\u2588\u2588\u2588\u2588\u2588\u2588\u2591\u2591\u2591\u2591] 60%", bar10(60))
    }

    @Test
    fun cleanRemovesColorCodesAndTimestamps() {
        val raw = "\u001B[31mError\u001B[0m\n2024-01-01T10:00:00.1234567Z hello"
        assertEquals("Error\nhello", clean(raw))
    }

    @Test
    fun unzipDropsTheTopFolderOfTheArchive() {
        val bytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("owner-repo-abc123/README.md"))
                zip.write("hi".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("owner-repo-abc123/src/Main.kt"))
                zip.write("fun main() {}".toByteArray())
                zip.closeEntry()
            }
            bos.toByteArray()
        }
        val out = tmp.newFolder("out")
        unzip(ByteArrayInputStream(bytes), out)
        assertEquals("hi", File(out, "README.md").readText())
        assertEquals("fun main() {}", File(out, "src/Main.kt").readText())
    }

    @Test
    fun filesSkipsMetadataAndGitFolders() {
        val repo = tmp.newFolder("repo")
        File(repo, "a.txt").writeText("a")
        File(repo, ".gab").writeText("owner/repo\nmain")
        File(repo, ".git").mkdirs()
        File(repo, ".git/config").writeText("x")
        val names = files(repo).map { it.name }
        assertEquals(listOf("a.txt"), names)
        assertTrue(names.none { it.startsWith(".") })
        assertFalse(names.contains("config"))
    }
}
