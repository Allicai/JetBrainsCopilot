package com.github.allicai.jetbrainscopilot.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class CopilotAction : AnAction("Copilot Complete", "AI-powered code completion", null) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor: Editor? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR)
        val project: Project? = e.project

        if (editor == null || project == null) {
            Messages.showMessageDialog(project, "No active editor found.", "Error", Messages.getErrorIcon())
            return
        }

        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val context = document.getText().substring(0, caretOffset).takeLast(300)

        val prompt = "Complete the following code:\n$context"

        callOpenAI(prompt) { completion ->
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    document.insertString(caretOffset, completion)
                }
            }
        }
    }

    private fun callOpenAI(prompt: String, callback: (String) -> Unit) {
        val apiKey = System.getenv("OPENAI_API_KEY")
                ?: return callback("// ERROR: No API key found")

        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val json = """
            {
            "model": "gpt-3.5-turbo-instruct",
            "prompt": "$prompt",
            "max_tokens": 100
            }
            """.trimIndent()

        val requestBody = json.toRequestBody(mediaType)


        val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("// ERROR: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val match = Regex("\"content\"\\s*:\\s*\"([^\"]+)\"").find(body)
                val suggestion = match?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")
                        ?: "// No response"
                callback(suggestion)
            }
        })
    }
}
