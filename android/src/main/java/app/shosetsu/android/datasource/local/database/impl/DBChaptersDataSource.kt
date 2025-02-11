package app.shosetsu.android.datasource.local.database.impl

import android.database.sqlite.SQLiteException
import app.shosetsu.android.common.ext.toDB
import app.shosetsu.android.providers.database.dao.ChaptersDao
import app.shosetsu.common.GenericSQLiteException
import app.shosetsu.common.datasource.database.base.IDBChaptersDataSource
import app.shosetsu.common.domain.model.local.ChapterEntity
import app.shosetsu.common.domain.model.local.ReaderChapterEntity
import app.shosetsu.common.dto.convertList
import app.shosetsu.lib.Novel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/*
 * This file is part of Shosetsu.
 *
 * Shosetsu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shosetsu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Shosetsu.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Shosetsu
 * 10 / May / 2020
 *
 * @author github.com/doomsdayrs
 */
class DBChaptersDataSource(
	private val chaptersDao: ChaptersDao,
) : IDBChaptersDataSource {

	@Throws(GenericSQLiteException::class)
	override suspend fun getChaptersFlow(
		novelID: Int,
	): Flow<List<ChapterEntity>> = flow {
		try {
			emitAll(chaptersDao.getChaptersFlow(novelID).map { it.convertList() })
		} catch (e: SQLiteException) {
			throw GenericSQLiteException(e)
		}
	}

	@Throws(GenericSQLiteException::class)
	override suspend fun getChapters(novelID: Int): List<ChapterEntity> = try {
		(chaptersDao.getChapters(novelID).convertList())
	} catch (e: SQLiteException) {
		throw GenericSQLiteException(e)
	}

	@Throws(GenericSQLiteException::class)
	override suspend fun getChaptersByExtension(extensionId: Int): List<ChapterEntity> =
		try {
			(chaptersDao.getChaptersByExtension(extensionId).convertList())
		} catch (e: SQLiteException) {
			throw GenericSQLiteException(e)
		}

	@Throws(GenericSQLiteException::class)
	override suspend fun getChapter(chapterID: Int): ChapterEntity? = try {
		chaptersDao.getChapter(chapterID)?.convertTo()
	} catch (e: SQLiteException) {
		throw GenericSQLiteException(e)
	}

	override suspend fun getReaderChapters(
		novelID: Int,
	): Flow<List<ReaderChapterEntity>> = flow {
		try {
			emitAll(chaptersDao.getReaderChaptersFlow(novelID))
		} catch (e: SQLiteException) {
			throw GenericSQLiteException(e)
		}
	}

	override suspend fun handleChapters(
		novelID: Int,
		extensionID: Int,
		list: List<Novel.Chapter>,
	): Unit =
		try {
			chaptersDao.handleNewData(novelID, extensionID, list)
		} catch (e: SQLiteException) {
			throw GenericSQLiteException(e)
		}


	@Throws(IndexOutOfBoundsException::class, GenericSQLiteException::class)
	override suspend fun handleChapterReturn(
		novelID: Int,
		extensionID: Int,
		list: List<Novel.Chapter>,
	): List<ChapterEntity> = try {
		chaptersDao.handleNewDataReturn(novelID, extensionID, list).convertList()
	} catch (e: SQLiteException) {
		throw GenericSQLiteException(e)
	}

	@Throws(GenericSQLiteException::class)
	override suspend fun updateChapter(chapterEntity: ChapterEntity): Unit = try {
		chaptersDao.update(chapterEntity.toDB())
	} catch (e: SQLiteException) {
		throw GenericSQLiteException(e)
	}

	override suspend fun updateReaderChapter(readerChapterEntity: ReaderChapterEntity): Unit =
		try {
			chaptersDao.update(readerChapterEntity)
		} catch (e: SQLiteException) {
			throw GenericSQLiteException(e)
		}

	@Throws(GenericSQLiteException::class)
	override suspend fun delete(entity: ChapterEntity): Unit =
		try {
			chaptersDao.delete(entity.toDB())
		} catch (e: SQLiteException) {
			throw GenericSQLiteException(e)
		}

}