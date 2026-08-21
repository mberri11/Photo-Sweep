package com.simobr.photosweep.data

import android.content.ContentResolver
import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle

/**
 * A Cursor that generates its rows on demand.
 *
 * Implements the interface rather than wrapping MatrixCursor because a JVM unit test has no
 * Android runtime: `android.database.Cursor` is an interface, so implementing it costs
 * nothing, while instantiating MatrixCursor would hit an unimplemented stub.
 *
 * Rows are produced by [cell] when asked for, never stored, so a 10,000-row cursor costs
 * nothing to create and the test measures the repository's appetite rather than its own.
 */
class FakeCursor(
    private val columns: Array<String>,
    private val rowCount: Int,
    private val cell: (row: Int, column: String) -> Any?,
) : Cursor {

    private var position = -1
    private var closed = false

    /** How many rows the consumer actually walked. */
    var rowsRead: Int = 0
        private set

    val isCursorClosed: Boolean get() = closed

    private fun value(columnIndex: Int): Any? = cell(position, columns[columnIndex])

    override fun getCount(): Int = rowCount
    override fun getPosition(): Int = position

    override fun move(offset: Int): Boolean = moveToPosition(position + offset)

    override fun moveToPosition(position: Int): Boolean {
        this.position = position.coerceIn(-1, rowCount)
        if (this.position in 0 until rowCount) rowsRead++
        return this.position in 0 until rowCount
    }

    override fun moveToFirst(): Boolean = moveToPosition(0)
    override fun moveToLast(): Boolean = moveToPosition(rowCount - 1)
    override fun moveToNext(): Boolean = moveToPosition(position + 1)
    override fun moveToPrevious(): Boolean = moveToPosition(position - 1)

    override fun isFirst(): Boolean = position == 0 && rowCount > 0
    override fun isLast(): Boolean = position == rowCount - 1 && rowCount > 0
    override fun isBeforeFirst(): Boolean = position < 0
    override fun isAfterLast(): Boolean = position >= rowCount

    override fun getColumnIndex(columnName: String): Int = columns.indexOf(columnName)

    override fun getColumnIndexOrThrow(columnName: String): Int =
        columns.indexOf(columnName).also {
            require(it >= 0) { "no such column: $columnName" }
        }

    override fun getColumnName(columnIndex: Int): String = columns[columnIndex]
    override fun getColumnNames(): Array<String> = columns
    override fun getColumnCount(): Int = columns.size

    override fun getBlob(columnIndex: Int): ByteArray = ByteArray(0)
    override fun getString(columnIndex: Int): String? = value(columnIndex)?.toString()
    override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer?) = Unit
    override fun getShort(columnIndex: Int): Short = getLong(columnIndex).toShort()
    override fun getInt(columnIndex: Int): Int = getLong(columnIndex).toInt()
    override fun getLong(columnIndex: Int): Long = (value(columnIndex) as? Number)?.toLong() ?: 0L
    override fun getFloat(columnIndex: Int): Float = getLong(columnIndex).toFloat()
    override fun getDouble(columnIndex: Int): Double = getLong(columnIndex).toDouble()

    override fun getType(columnIndex: Int): Int = when (value(columnIndex)) {
        null -> Cursor.FIELD_TYPE_NULL
        is Number -> Cursor.FIELD_TYPE_INTEGER
        else -> Cursor.FIELD_TYPE_STRING
    }

    override fun isNull(columnIndex: Int): Boolean = value(columnIndex) == null

    @Deprecated("Deprecated in Cursor", ReplaceWith(""))
    override fun deactivate() = Unit

    @Deprecated("Deprecated in Cursor", ReplaceWith(""))
    override fun requery(): Boolean = false

    override fun close() { closed = true }
    override fun isClosed(): Boolean = closed

    override fun registerContentObserver(observer: ContentObserver?) = Unit
    override fun unregisterContentObserver(observer: ContentObserver?) = Unit
    override fun registerDataSetObserver(observer: DataSetObserver?) = Unit
    override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit

    override fun setNotificationUri(cr: ContentResolver?, uri: Uri?) = Unit
    override fun getNotificationUri(): Uri? = null
    override fun getWantsAllOnMoveCalls(): Boolean = false

    override fun setExtras(extras: Bundle?) = Unit
    override fun getExtras(): Bundle? = null
    override fun respond(extras: Bundle?): Bundle? = null
}
