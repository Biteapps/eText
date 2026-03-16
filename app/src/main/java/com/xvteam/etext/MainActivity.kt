package com.xvteam.etext

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.IOException
import java.util.Base64

class MainActivity : AppCompatActivity() {

    private lateinit var etEditor: EditText
    private lateinit var tvFileName: TextView
    private lateinit var tvWordCount: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvCursorPos: TextView

    private var currentFileName = "untitled.txt"
    private var isModified = false
    private val history = mutableListOf<String>()
    private var historyIndex = -1
    private var currentTextSize = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        saveToHistory()
    }

    private fun initViews() {
        etEditor = findViewById(R.id.etEditor)
        tvFileName = findViewById(R.id.tvFileName)
        tvWordCount = findViewById(R.id.tvWordCount)
        tvStatus = findViewById(R.id.tvStatus)
        tvCursorPos = findViewById(R.id.tvCursorPos)

        // Основные кнопки
        findViewById<Button>(R.id.btnNew).setOnClickListener { newFile() }
        findViewById<Button>(R.id.btnOpen).setOnClickListener { openFile() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveFile() }
        findViewById<Button>(R.id.btnUndo).setOnClickListener { undo() }
        findViewById<Button>(R.id.btnCut).setOnClickListener { cutText() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyText() }
        findViewById<Button>(R.id.btnPaste).setOnClickListener { pasteText() }

        // Форматирование
        findViewById<Button>(R.id.btnBold).setOnClickListener { applyFormat(Format.BOLD) }
        findViewById<Button>(R.id.btnItalic).setOnClickListener { applyFormat(Format.ITALIC) }
        findViewById<Button>(R.id.btnSizePlus).setOnClickListener { changeTextSize(0.2f) }
        findViewById<Button>(R.id.btnSizeMinus).setOnClickListener { changeTextSize(-0.2f) }

        // Калькулятор и Base64
        findViewById<Button>(R.id.btnCalc).setOnClickListener { showCalculator() }
        findViewById<Button>(R.id.btnBase64).setOnClickListener { showBase64Converter() }
    }

    private enum class Format { BOLD, ITALIC }

    private fun applyFormat(format: Format) {
        val start = etEditor.selectionStart
        val end = etEditor.selectionEnd

        if (start == end) {
            Toast.makeText(this, "Выделите текст для форматирования", Toast.LENGTH_SHORT).show()
            return
        }

        val text = etEditor.text as? SpannableStringBuilder ?: SpannableStringBuilder(etEditor.text)
        val style = when (format) {
            Format.BOLD -> Typeface.BOLD
            Format.ITALIC -> Typeface.ITALIC
        }

        val existingSpans = text.getSpans(start, end, StyleSpan::class.java)
        for (span in existingSpans) {
            if (span.style == style) {
                text.removeSpan(span)
            }
        }

        text.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        etEditor.text = text
        etEditor.setSelection(start, end)
        updateStatus(if (format == Format.BOLD) "Жирный текст" else "Курсив")
    }

    private fun changeTextSize(delta: Float) {
        currentTextSize = (currentTextSize + delta).coerceIn(0.5f, 3.0f)
        etEditor.textSize = 16f * currentTextSize
        updateStatus("Размер: ${(currentTextSize * 100).toInt()}%")
    }

    private fun showCalculator() {
        val input = EditText(this).apply {
            hint = "Например: 2+2*3 или 10/2+5"
            textSize = 16f
        }

        AlertDialog.Builder(this)
            .setTitle("Калькулятор")
            .setView(input)
            .setPositiveButton("Вычислить") { _, _ ->
                val expression = input.text.toString()
                val result = calculate(expression)
                input.setText(result)
                input.setSelection(0, result.length)
            }
            .setNeutralButton("Вставить в текст") { _, _ ->
                val expression = input.text.toString()
                val result = calculate(expression)
                val cursorPos = etEditor.selectionStart
                etEditor.text.insert(cursorPos, "$expression = $result")
                updateStatus("Вычислено: $result")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun calculate(expression: String): String {
        return try {
            val result = evalSimpleExpression(expression.replace(" ", ""))
            result.toString()
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }

    private fun evalSimpleExpression(expr: String): Double {
        return parseExpression(expr)
    }

    private fun parseExpression(s: String): Double {
        var expression = s

        while ('(' in expression) {
            val start = expression.lastIndexOf('(')
            val end = expression.indexOf(')', start)
            if (end == -1) throw IllegalArgumentException("Незакрытая скобка")
            val inner = expression.substring(start + 1, end)
            val result = parseSimple(inner)
            expression = expression.substring(0, start) + result + expression.substring(end + 1)
        }

        return parseSimple(expression)
    }

    private fun parseSimple(expr: String): Double {
        var s = expr

        val mulDivRegex = Regex("""(\d+\.?\d*)([*/])(\d+\.?\d*)""")
        while (mulDivRegex.containsMatchIn(s)) {
            s = mulDivRegex.replace(s) { match ->
                val left = match.groupValues[1].toDouble()
                val op = match.groupValues[2]
                val right = match.groupValues[3].toDouble()
                val res = if (op == "*") left * right else left / right
                res.toString()
            }
        }

        val addSubRegex = Regex("""(\d+\.?\d*)([+-])(\d+\.?\d*)""")
        while (addSubRegex.containsMatchIn(s)) {
            s = addSubRegex.replace(s) { match ->
                val left = match.groupValues[1].toDouble()
                val op = match.groupValues[2]
                val right = match.groupValues[3].toDouble()
                val res = if (op == "+") left + right else left - right
                res.toString()
            }
        }

        return s.toDouble()
    }

    private fun showBase64Converter() {
        val options = arrayOf("Кодировать в Base64", "Декодировать из Base64", "Кодировать выделенный текст", "Декодировать выделенный текст")

        AlertDialog.Builder(this)
            .setTitle("Base64 Конвертер")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> encodeBase64Dialog()
                    1 -> decodeBase64Dialog()
                    2 -> encodeSelectedText()
                    3 -> decodeSelectedText()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun encodeBase64Dialog() {
        val input = EditText(this).apply {
            hint = "Введите текст для кодирования"
        }

        AlertDialog.Builder(this)
            .setTitle("Base64 Кодирование")
            .setView(input)
            .setPositiveButton("Кодировать") { _, _ ->
                val text = input.text.toString()
                val encoded = encodeBase64(text)
                input.setText(encoded)
                input.setSelection(0, encoded.length)
            }
            .setNeutralButton("Вставить в текст") { _, _ ->
                val text = input.text.toString()
                val encoded = encodeBase64(text)
                val cursorPos = etEditor.selectionStart
                etEditor.text.insert(cursorPos, encoded)
                updateStatus("Закодировано в Base64")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun decodeBase64Dialog() {
        val input = EditText(this).apply {
            hint = "Введите Base64 для декодирования"
        }

        AlertDialog.Builder(this)
            .setTitle("Base64 Декодирование")
            .setView(input)
            .setPositiveButton("Декодировать") { _, _ ->
                val text = input.text.toString()
                val decoded = decodeBase64(text)
                input.setText(decoded)
                input.setSelection(0, decoded.length)
            }
            .setNeutralButton("Вставить в текст") { _, _ ->
                val text = input.text.toString()
                val decoded = decodeBase64(text)
                val cursorPos = etEditor.selectionStart
                etEditor.text.insert(cursorPos, decoded)
                updateStatus("Декодировано из Base64")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun encodeSelectedText() {
        val start = etEditor.selectionStart
        val end = etEditor.selectionEnd

        if (start == end) {
            Toast.makeText(this, "Выделите текст для кодирования", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedText = etEditor.text.substring(start, end)
        val encoded = encodeBase64(selectedText)
        etEditor.text.replace(start, end, encoded)
        updateStatus("Выделенный текст закодирован")
    }

    private fun decodeSelectedText() {
        val start = etEditor.selectionStart
        val end = etEditor.selectionEnd

        if (start == end) {
            Toast.makeText(this, "Выделите Base64 для декодирования", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedText = etEditor.text.substring(start, end)
        val decoded = decodeBase64(selectedText)
        etEditor.text.replace(start, end, decoded)
        updateStatus("Выделенный текст декодирован")
    }

    private fun encodeBase64(text: String): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
            } else {
                android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8), android.util.Base64.DEFAULT)
            }
        } catch (e: Exception) {
            "Ошибка кодирования"
        }
    }

    private fun decodeBase64(text: String): String {
        return try {
            val bytes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Base64.getDecoder().decode(text)
            } else {
                android.util.Base64.decode(text, android.util.Base64.DEFAULT)
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Ошибка декодирования: неверный Base64"
        }
    }

    private fun setupListeners() {
        etEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateWordCount()
                updateCursorPosition()
                if (!isModified) {
                    isModified = true
                    updateTitle()
                }
            }

            override fun afterTextChanged(s: Editable?) {
                etEditor.removeCallbacks(saveHistoryRunnable)
                etEditor.postDelayed(saveHistoryRunnable, 1000)
            }
        })

        etEditor.setOnClickListener { updateCursorPosition() }
    }

    private val saveHistoryRunnable = Runnable { saveToHistory() }

    private fun saveToHistory() {
        val text = etEditor.text.toString()
        if (history.isEmpty() || history.last() != text) {
            while (history.size > historyIndex + 1) {
                history.removeAt(history.size - 1)
            }
            history.add(text)
            historyIndex++
            if (history.size > 50) {
                history.removeAt(0)
                historyIndex--
            }
        }
    }

    private fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            etEditor.setText(history[historyIndex])
            etEditor.setSelection(history[historyIndex].length)
            updateStatus("Отменено")
        } else {
            Toast.makeText(this, "Нечего отменять", Toast.LENGTH_SHORT).show()
        }
    }

    private fun newFile() {
        if (isModified) {
            AlertDialog.Builder(this)
                .setTitle("Сохранить изменения?")
                .setMessage("Текущий файл изменён. Сохранить?")
                .setPositiveButton("Сохранить") { _, _ ->
                    saveFile()
                    clearEditor()
                }
                .setNegativeButton("Не сохранять") { _, _ ->
                    clearEditor()
                }
                .setNeutralButton("Отмена", null)
                .show()
        } else {
            clearEditor()
        }
    }

    private fun clearEditor() {
        etEditor.text.clear()
        currentFileName = "untitled.txt"
        isModified = false
        history.clear()
        historyIndex = -1
        saveToHistory()
        updateTitle()
        updateWordCount()
        updateStatus("Новый файл")
    }

    private fun openFile() {
        val files = filesDir.listFiles { file -> file.extension == "txt" }

        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "Нет сохранённых файлов", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Открыть файл")
            .setItems(fileNames) { _, which ->
                loadFile(files[which])
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadFile(file: File) {
        try {
            val content = file.readText()
            etEditor.setText(content)
            currentFileName = file.name
            isModified = false
            history.clear()
            historyIndex = -1
            saveToHistory()
            updateTitle()
            updateWordCount()
            updateStatus("Открыто: ${file.name}")
        } catch (e: IOException) {
            Toast.makeText(this, "Ошибка открытия: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFile() {
        val content = etEditor.text.toString()
        if (content.isEmpty()) {
            Toast.makeText(this, "Нечего сохранять", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            setText(currentFileName)
            setSelection(0, currentFileName.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Сохранить как")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val fileName = input.text.toString().ifEmpty { "untitled.txt" }
                val finalName = if (fileName.endsWith(".txt")) fileName else "$fileName.txt"
                performSave(finalName, content)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performSave(fileName: String, content: String) {
        try {
            val file = File(filesDir, fileName)
            file.writeText(content)
            currentFileName = fileName
            isModified = false
            updateTitle()
            updateStatus("Сохранено: $fileName")
            Toast.makeText(this, "Файл сохранён", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cutText() {
        val start = etEditor.selectionStart
        val end = etEditor.selectionEnd
        if (start != end) {
            val selectedText = etEditor.text.substring(start, end)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", selectedText))
            etEditor.text.delete(start, end)
            updateStatus("Вырезано")
        }
    }

    private fun copyText() {
        val start = etEditor.selectionStart
        val end = etEditor.selectionEnd
        if (start != end) {
            val selectedText = etEditor.text.substring(start, end)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", selectedText))
            updateStatus("Скопировано")
        }
    }

    private fun pasteText() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val item = clipboard.primaryClip?.getItemAt(0)
            val pasteText = item?.text?.toString() ?: ""
            val start = etEditor.selectionStart
            etEditor.text.insert(start, pasteText)
            updateStatus("Вставлено")
        }
    }

    private fun updateWordCount() {
        val text = etEditor.text.toString()
        val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
        val chars = text.length
        tvWordCount.text = "$words слов | $chars симв"
    }

    private fun updateCursorPosition() {
        val pos = etEditor.selectionStart
        val text = etEditor.text.toString()
        val line = text.substring(0, pos.coerceAtMost(text.length)).count { it == '\n' } + 1
        val column = pos - (text.lastIndexOf('\n', pos - 1).coerceAtLeast(-1) + 1) + 1
        tvCursorPos.text = "Стр: $line, Стлб: $column"
    }

    private fun updateTitle() {
        val marker = if (isModified) " *" else ""
        tvFileName.text = "$currentFileName$marker"
    }

    private fun updateStatus(message: String) {
        tvStatus.text = message
        tvStatus.postDelayed({
            if (tvStatus.text == message) {
                tvStatus.text = "Готово"
            }
        }, 3000)
    }

    override fun onBackPressed() {
        if (isModified) {
            AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Сохранить изменения перед выходом?")
                .setPositiveButton("Сохранить") { _, _ ->
                    saveFile()
                    finish()
                }
                .setNegativeButton("Не сохранять") { _, _ ->
                    finish()
                }
                .setNeutralButton("Отмена", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}