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
 * `mediaId` is the primary key rather than a composite with `pileId`. It used to be justified
 * by "a photo appears in exactly one pile", which stopped being true when the overlay piles
 * arrived — a photo can be in Biggest files *and* in its month pile at once. The key is still
 * right, for a better reason: a mark is a decision about a **photo**, not about the pile the
 * user happened to be looking at when they made it. One row per photo is what makes
 * `PendingMarkDao.markedMediaIds()` a complete answer, and what stops the same photo being
 * offered twice through two different lenses.
 */
@Entity(tableName = "pending_mark")
data class PendingMark(
    @PrimaryKey val mediaId: Long,
    val pileId: String,
    val markedAt: Long,
)
