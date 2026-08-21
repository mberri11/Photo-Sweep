package com.simobr.photosweep.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A photo the user has swiped left on, but which has **not been touched**.
 *
 * Nothing is trashed until the confirm screen. This table is a record of intent and nothing
 * more, which is exactly why undo is free all the way back to the first photo.
 *
 * It exists because of one failure mode: a user two hundred swipes into a pile takes a phone
 * call, the process is killed, and every one of those decisions evaporates. That user
 * uninstalls, and they are right to.
 *
 * `mediaId` is the primary key rather than a composite with `pileId`: a photo appears in
 * exactly one pile, and a second mark for the same photo is a correction, not a new fact.
 */
@Entity(tableName = "pending_mark")
data class PendingMark(
    @PrimaryKey val mediaId: Long,
    val pileId: String,
    val markedAt: Long,
)
