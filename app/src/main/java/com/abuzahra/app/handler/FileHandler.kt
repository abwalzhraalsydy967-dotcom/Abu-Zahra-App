package com.abuzahra.app.handler

import android.content.Context
import android.content.pm.PackageManager

class FileHandler(private val context: Context) {

    fun listFiles(params: Map<*, *>): Any {
        return try {
            val path = params["path"] as? String ?: "/sdcard"
            val dir = java.io.File(path)
            if (!dir.exists() || !dir.isDirectory) {
                return mapOf("status" to "error", "message" to "Directory not found: $path")
            }
            val files = dir.listFiles()
            val fileList = files?.take(100)?.map { file ->
                mapOf(
                    "name" to file.name,
                    "path" to file.absolutePath,
                    "isDirectory" to file.isDirectory,
                    "size" to file.length(),
                    "lastModified" to file.lastModified()
                )
            } ?: emptyList()
            mapOf("status" to "success", "path" to path, "count" to fileList.size, "data" to fileList)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getFile(params: Map<*, *>): Any {
        return try {
            val path = params["path"] as? String ?: return mapOf("status" to "error", "message" to "Path required")
            val file = java.io.File(path)
            if (!file.exists()) {
                return mapOf("status" to "error", "message" to "File not found: $path")
            }
            if (file.length() > 5 * 1024 * 1024) {
                return mapOf("status" to "error", "message" to "File too large (max 5MB)")
            }
            val content = if (file.length() < 100 * 1024 && file.canRead()) {
                file.readText()
            } else {
                "[Binary file - ${file.length()} bytes]"
            }
            mapOf("status" to "success", "path" to path, "name" to file.name, "size" to file.length(), "content" to content)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun deleteFile(params: Map<*, *>): Any {
        return try {
            val path = params["path"] as? String ?: return mapOf("status" to "error", "message" to "Path required")
            val file = java.io.File(path)
            if (!file.exists()) {
                return mapOf("status" to "error", "message" to "File not found")
            }
            val deleted = file.delete()
            mapOf("status" to if (deleted) "success" else "error", "path" to path)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun searchFiles(params: Map<*, *>): Any {
        return try {
            val query = params["query"] as? String ?: return mapOf("status" to "error", "message" to "Query required")
            val searchPath = params["path"] as? String ?: "/sdcard"
            val results = mutableListOf<Map<String, Any>>()
            searchInDir(java.io.File(searchPath), query, results, 50)
            mapOf("status" to "success", "query" to query, "count" to results.size, "data" to results)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun recentFiles(): Any {
        return try {
            val results = mutableListOf<Map<String, Any>>()
            searchRecentInDir(java.io.File("/sdcard"), results, 30, System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000)
            results.sortByDescending { it["lastModified"] as Long }
            mapOf("status" to "success", "count" to results.size, "data" to results)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    private fun searchInDir(dir: java.io.File, query: String, results: MutableList<Map<String, Any>>, limit: Int) {
        if (results.size >= limit) return
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (results.size >= limit) break
                if (file.name.contains(query, ignoreCase = true)) {
                    results.add(mapOf(
                        "name" to file.name,
                        "path" to file.absolutePath,
                        "isDirectory" to file.isDirectory,
                        "size" to file.length(),
                        "lastModified" to file.lastModified()
                    ))
                }
                if (file.isDirectory && results.size < limit) {
                    searchInDir(file, query, results, limit)
                }
            }
        } catch (_: Exception) {}
    }

    private fun searchRecentInDir(dir: java.io.File, results: MutableList<Map<String, Any>>, limit: Int, minTime: Long) {
        if (results.size >= limit) return
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (results.size >= limit) break
                if (file.lastModified() > minTime && !file.isDirectory) {
                    results.add(mapOf(
                        "name" to file.name,
                        "path" to file.absolutePath,
                        "size" to file.length(),
                        "lastModified" to file.lastModified()
                    ))
                }
                if (file.isDirectory) {
                    searchRecentInDir(file, results, limit, minTime)
                }
            }
        } catch (_: Exception) {}
    }
}
