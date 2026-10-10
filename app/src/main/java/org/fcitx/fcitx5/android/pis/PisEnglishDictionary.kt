/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** WordNet 3.1 definitions, synonyms, examples and irregular inflections; fully offline. */
object PisEnglishDictionary {
    private val mutex = Mutex()
    private var database: SQLiteDatabase? = null
    private const val hash = "0e00d480d330af22638e89bb752d62addb6fda6c2d7a8738ae847dd7480194ae"
    private fun db(context: Context): SQLiteDatabase {
        database?.let { return it }
        val target = File(context.noBackupFilesDir,"wordnet31-${hash.take(12)}.db")
        if (!target.exists()) {
            val temporary = File(target.path+".part"); val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("pis-input/wordnet31.db").use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer,0,size); output.write(buffer,0,size) }
            } }
            check(digest.digest().joinToString("") { "%02x".format(it) } == hash)
            check(temporary.renameTo(target))
        }
        return SQLiteDatabase.openDatabase(target.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).also { database = it }
    }
    /** Share irregular forms with the bilingual dictionary, e.g. went -> go. */
    suspend fun baseForms(context: Context, word: String): List<String> = withContext(Dispatchers.IO) {
        if (word.length > 64 || !context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) return@withContext emptyList()
        mutex.withLock {
            runCatching {
                val forms = mutableListOf<String>()
                db(context).rawQuery("SELECT lemma FROM form WHERE word=?",arrayOf(word.lowercase(Locale.ROOT))).use {
                    while (it.moveToNext()) forms += it.getString(0)
                }
                forms
            }.getOrDefault(emptyList())
        }
    }
    suspend fun lookup(context: Context, word: String): String = withContext(Dispatchers.IO) {
        if (word.length > 64 || word.none { it.isLetter() } || !context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) return@withContext ""
        mutex.withLock {
            runCatching {
                val data = db(context); val normalized = word.lowercase(Locale.ROOT).trim().replace('_',' ')
                val forms = linkedSetOf(normalized)
                data.rawQuery("SELECT lemma FROM form WHERE word=?",arrayOf(normalized)).use { while (it.moveToNext()) forms += it.getString(0) }
                for (suffix in listOf("'s","s","es","ed","ing")) if (normalized.endsWith(suffix) && normalized.length > suffix.length+2) {
                    val stem = normalized.dropLast(suffix.length); forms += stem
                    if (suffix in listOf("ed","ing")) {
                        forms += stem+"e"
                        if (stem.length > 2 && stem.last() == stem[stem.length-2]) forms += stem.dropLast(1)
                    }
                }
                val meanings = mutableListOf<String>()
                for (form in forms) {
                    data.rawQuery("SELECT s.pos,d.definition,d.examples,d.synonyms FROM sense s JOIN synset d ON s.synset=d.id WHERE s.word=? ORDER BY s.rank,CASE s.pos WHEN 'n' THEN 0 WHEN 'v' THEN 1 WHEN 'a' THEN 2 ELSE 3 END LIMIT 12",arrayOf(form)).use {
                        while (it.moveToNext()) {
                            val pos = when (it.getString(0)) { "n" -> "n."; "v" -> "v."; "a" -> "adj."; else -> "adv." }
                            val definition = it.getString(1); val examples = it.getString(2); val synonyms = it.getString(3)
                            meanings += buildString {
                                append("${meanings.size+1}. $pos $definition")
                                if (examples.isNotBlank()) append("\nExamples: $examples")
                                if (synonyms.isNotBlank()) append("\nSynonyms: $synonyms")
                            }
                        }
                    }
                    if (meanings.isNotEmpty()) break
                }
                meanings.joinToString("\n\n")
            }.getOrDefault("")
        }
    }
}
