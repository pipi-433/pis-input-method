/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Read-only packaged dictionary. Copy and indexed lookups stay off the keyboard thread. */
object PisGlossary {
    data class Entry(val hint: String, val definition: String)
    private val empty = Entry("", "")
    private val mutex = Mutex()
    private val cache = LruCache<String, Entry>(1024)
    private val readingFilter = Regex("[^a-z]")
    private fun cacheKey(word: String, reading: String, withEnglishDefinitions: Boolean) =
        "$withEnglishDefinitions|$word|${reading.lowercase(Locale.ROOT).replace(readingFilter, "")}"
    /** LruCache synchronizes its entries; this never opens or queries SQLite on the UI thread. */
    fun cachedLegacyHint(word: String, reading: String = ""): String? =
        cache.get(cacheKey(word, reading, false))?.hint
    private var database: SQLiteDatabase? = null
    private const val hash = "58778b70d32cdf1539f2e751b7ff92b8f950784b60c524a6ef9a3adfa898ab62"
    private fun db(context: Context): SQLiteDatabase {
        database?.let { return it }
        val file = File(context.noBackupFilesDir,"pis-gloss-${hash.take(12)}.db")
        if (!file.exists()) {
            val temporary = File(file.path+".part")
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("pis-input/gloss.db").use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer,0,size); output.write(buffer,0,size) }
            } }
            check(digest.digest().joinToString("") { "%02x".format(it) } == hash) { "释义库校验失败" }
            check(temporary.renameTo(file)) { "无法准备释义库" }
        }
        return SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).also { database = it }
    }
    suspend fun lookupLegacy(context: Context, word: String, reading: String = ""): Entry =
        lookup(context, word, reading, withEnglishDefinitions = false)

    suspend fun englishTranslation(context: Context, word: String, reading: String = ""): String? {
        if (word.none { it in '\u3400'..'\u9fff' || it in '\uf900'..'\ufaff' }) return null
        return englishHint(lookupLegacy(context, word, reading).hint)
    }

    fun englishHint(hint: String): String? = PisCandidateEnglish.fromHint(hint)

    suspend fun lookup(context: Context, word: String, reading: String = "", withEnglishDefinitions: Boolean = true): Entry = withContext(Dispatchers.IO) {
        if (word.isBlank() || word.length > 40 || !context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) return@withContext empty
        val canonical = reading.lowercase(Locale.ROOT).replace(readingFilter,"")
        val key = cacheKey(word, reading, withEnglishDefinitions)
        mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            val result = runCatching {
                val data = db(context)
                if (word.any { it in '\u3400'..'\u9fff' }) {
                    var found = empty
                    for (table in listOf("zh","llm")) {
                        val rows = mutableListOf<Pair<String,String>>()
                        data.rawQuery("SELECT reading,gloss FROM $table WHERE word=? ORDER BY CASE WHEN reading=? AND reading<>'' THEN 0 WHEN reading='' THEN 1 ELSE 2 END LIMIT 12",arrayOf(word,canonical)).use {
                            while (it.moveToNext()) rows += it.getString(0) to it.getString(1)
                        }
                        if (rows.isNotEmpty()) {
                            val primary = rows.first().second
                            val short = primary.replace(Regex("\\([^)]*\\)|\\[[^]]*]|（[^）]*）"),"").substringBefore(';').substringBefore('\n').trim().replace(Regex("\\s+")," ")
                            val definition = rows.joinToString("\n\n") { (sound,meaning) -> if (sound.isEmpty()) meaning else "$sound\n$meaning" }
                            found = Entry(short,definition); break
                        }
                    }
                    if (withEnglishDefinitions && found.hint.matches(Regex("[A-Za-z][A-Za-z '\\-]{0,63}"))) {
                        val english = PisEnglishDictionary.lookup(context,found.hint)
                        if (english.isNotBlank()) found = Entry(found.hint,"${found.definition}\n\nEnglish definitions · ${found.hint} (WordNet 3.1)\n$english")
                    }
                    if (found.definition.isBlank()) found else Entry(found.hint,"中文词条\n$word\n\n英文译义\n${found.definition}")
                } else {
                    val normalized = word.trim().lowercase(Locale.ROOT)
                    val forms = linkedSetOf(normalized)
                    if (withEnglishDefinitions) forms += PisEnglishDictionary.baseForms(context,normalized)
                    for (suffix in listOf("'s","s","es","ed","ing")) if (normalized.endsWith(suffix) && normalized.length > suffix.length+2) {
                        val stem = normalized.dropLast(suffix.length); forms += stem
                        if (suffix in listOf("ed","ing")) forms += stem+"e"
                        if (suffix in listOf("ed","ing") && stem.length > 2 && stem.last() == stem[stem.length-2]) forms += stem.dropLast(1)
                    }
                    var found = empty
                    for (form in forms) {
                        data.rawQuery("SELECT zh,phonetic,pos FROM en WHERE word=?",arrayOf(form)).use {
                            if (it.moveToFirst()) {
                                val meaning = it.getString(0).orEmpty(); val phonetic = it.getString(1).orEmpty(); val pos = it.getString(2).orEmpty()
                                found = Entry(meaning.substringBefore(';').substringBefore('；').substringBefore(','),listOf(phonetic,pos,meaning).filter { text -> text.isNotBlank() }.joinToString("\n\n"))
                            }
                        }
                        if (found != empty) break
                    }
                    if (!withEnglishDefinitions) {
                        return@runCatching if (found == empty) empty else Entry(found.hint,
                            "英文词条\n$word\n\n中文释义与音标\n${found.definition}")
                    }
                    val english = PisEnglishDictionary.lookup(context,normalized)
                    val needsChinese = found.definition.none { it in '\u3400'..'\u9fff' }
                    val body = buildString {
                        append("中文释义与音标\n")
                        append(if (needsChinese) "本地词典暂无中文释义" else found.definition)
                        append("\n\nEnglish definitions · WordNet 3.1\n")
                        append(english.ifBlank { "本地英英词典暂无释义" })
                    }
                    Entry(found.hint,body)
                }
            }.getOrElse { empty }
            cache.put(key,result); result
        }
    }
}
