package app.shosetsu.android.providers.database

import android.content.ContentValues
import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteException
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.shosetsu.android.common.ext.*
import app.shosetsu.android.domain.model.database.*
import app.shosetsu.android.providers.database.converters.*
import app.shosetsu.android.providers.database.dao.*
import app.shosetsu.android.providers.database.migrations.RemoveMigration
import app.shosetsu.lib.ExtensionType
import app.shosetsu.lib.Novel
import dev.matrix.roomigrant.GenerateRoomMigrations
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/*
 * This file is part of shosetsu.
 *
 * shosetsu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * shosetsu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with shosetsu.  If not, see <https://www.gnu.org/licenses/>.
 * ====================================================================
 */

/**
 * shosetsu
 * 17 / 04 / 2020
 *
 * @author github.com/doomsdayrs
 */
@Fts4
@Database(
	entities = [
		DBChapterEntity::class,
		DBDownloadEntity::class,
		DBInstalledExtensionEntity::class,
		DBRepositoryExtensionEntity::class,
		DBExtLibEntity::class,
		DBNovelReaderSettingEntity::class,
		DBNovelEntity::class,
		DBNovelSettingsEntity::class,
		DBRepositoryEntity::class,
		DBUpdate::class,
	],
	version = 6
)
@TypeConverters(
	ChapterSortTypeConverter::class,
	DownloadStatusConverter::class,
	ListConverter::class,
	NovelStatusConverter::class,
	ChapterTypeConverter::class,
	ReadingStatusConverter::class,
	StringArrayConverters::class,
	VersionConverter::class,
	ExtensionTypeConverter::class
)
@GenerateRoomMigrations
abstract class ShosetsuDatabase : RoomDatabase() {

	abstract val chaptersDao: ChaptersDao
	abstract val downloadsDao: DownloadsDao
	abstract val extensionLibraryDao: ExtensionLibraryDao
	abstract val installedExtensionsDao: InstalledExtensionsDao
	abstract val repositoryExtensionDao: RepositoryExtensionsDao
	abstract val novelReaderSettingsDao: NovelReaderSettingsDao
	abstract val novelsDao: NovelsDao
	abstract val novelSettingsDao: NovelSettingsDao
	abstract val repositoryDao: RepositoryDao
	abstract val updatesDao: UpdatesDao

	companion object {
		@Volatile
		private lateinit var databaseShosetsu: ShosetsuDatabase

		@Synchronized
		fun getRoomDatabase(context: Context): ShosetsuDatabase {
			if (!Companion::databaseShosetsu.isInitialized)
				databaseShosetsu = Room.databaseBuilder(
					context.applicationContext,
					ShosetsuDatabase::class.java,
					"room_database"
				).addMigrations(
					object : RemoveMigration(1, 2) {
						override fun migrate(database: SupportSQLiteDatabase) {
							deleteColumnFromTable(database, "chapters", "savePath")
						}
					},
					object : Migration(2, 3) {
						@Throws(SQLException::class)
						override fun migrate(database: SupportSQLiteDatabase) {
							// Handle repository migration
							run {
								val tableName = "repositories"

								// Creates new table
								database.execSQL("CREATE TABLE IF NOT EXISTS `${tableName}_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `url` TEXT NOT NULL UNIQUE, `name` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL)")

								// Migrate
								val cursor = database.query("SELECT * FROM $tableName")
								while (cursor.moveToNext()) {
									database.insert(
										"${tableName}_new",
										OnConflictStrategy.ABORT,
										ContentValues().apply {
											val keyURL = "url"
											val keyName = "name"
											put(
												keyURL,
												cursor.getColumnIndex(keyURL).takeIf { it >= 0 }
													?.let {
														cursor.getString(it)
													}
											)
											put(
												keyName,
												cursor.getColumnIndex(keyURL).takeIf { it >= 0 }
													?.let {
														cursor.getString(it)
													}
											)
											put("isEnabled", true)
										}
									)
								}

								// Drop
								database.execSQL("DROP TABLE $tableName")

								// Rename table_new to table
								database.execSQL("ALTER TABLE `${tableName}_new` RENAME TO `${tableName}`")

								// Creat indexes
								database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${tableName}_url` ON `${tableName}` (`url`)")
							}

							// Handle chapter migration
							run {
								val tableName = "chapters"

								// Create new table
								database.execSQL("CREATE TABLE IF NOT EXISTS `${tableName}_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `url` TEXT NOT NULL, `novelID` INTEGER NOT NULL, `formatterID` INTEGER NOT NULL, `title` TEXT NOT NULL, `releaseDate` TEXT NOT NULL, `order` REAL NOT NULL, `readingPosition` REAL NOT NULL, `readingStatus` INTEGER NOT NULL, `bookmarked` INTEGER NOT NULL, `isSaved` INTEGER NOT NULL, FOREIGN KEY(`novelID`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`formatterID`) REFERENCES `extensions`(`id`) ON UPDATE CASCADE ON DELETE SET NULL )")

								// Handle migration
								val cursor = database.query("SELECT * FROM $tableName")
								while (cursor.moveToNext()) {
									database.insert(
										"${tableName}_new",
										OnConflictStrategy.ABORT,
										ContentValues().apply {
											this["'id'"] = cursor.getInt("id")
											this["'url'"] = cursor.getString("url")
											this["'novelID'"] = cursor.getInt("novelID")
											this["'formatterID'"] = cursor.getInt("formatterID")
											this["'title'"] = cursor.getString("title")
											this["'releaseDate'"] = cursor.getString("releaseDate")
											this["'order'"] = cursor.getDouble("order")
											this["'readingPosition'"] = 0.0
											this["'readingStatus'"] = cursor.getInt("readingStatus")
											this["'bookmarked'"] = cursor.getInt("bookmarked")
											this["'isSaved'"] = cursor.getInt("isSaved")
										}
									)
								}

								// Drop
								database.execSQL("DROP TABLE $tableName")

								// Rename table_new to table
								database.execSQL("ALTER TABLE `${tableName}_new` RENAME TO `${tableName}`")

								// Create indexes
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_novelID` ON `${tableName}` (`novelID`)")
								database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_url` ON `${tableName}` (`url`)")
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_formatterID` ON `${tableName}` (`formatterID`)")
							}

							// Handle extension migration
							run {
								val tableName = "extensions"

								// Create new table
								database.execSQL("CREATE TABLE IF NOT EXISTS `${tableName}_new` (`id` INTEGER NOT NULL, `repoID` INTEGER NOT NULL, `name` TEXT NOT NULL, `fileName` TEXT NOT NULL, `imageURL` TEXT, `lang` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `installed` INTEGER NOT NULL, `installedVersion` TEXT, `repositoryVersion` TEXT NOT NULL, `chapterType` INTEGER NOT NULL, `md5` TEXT NOT NULL, `type` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`repoID`) REFERENCES `repositories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )\n")

								// Migrate
								val cursor = database.query("SELECT * FROM $tableName")
								while (cursor.moveToNext()) {
									database.insert(
										"${tableName}_new",
										OnConflictStrategy.ABORT,
										ContentValues().apply {
											this["'id'"] = cursor.getInt("id")
											this["'repoID'"] = cursor.getInt("repoID")
											this["'name'"] = cursor.getString("name")
											this["'fileName'"] = cursor.getString("fileName")
											this["'imageURL'"] = cursor.getString("imageURL")
											this["'lang'"] = cursor.getString("lang")
											this["'enabled'"] = cursor.getInt("enabled")
											this["'installed'"] = cursor.getInt("installed")
											this["'installedVersion'"] =
												cursor.getStringOrNull("installedVersion")
											this["'repositoryVersion'"] =
												cursor.getString("repositoryVersion")
											this["'chapterType'"] = 0
											this["'md5'"] = cursor.getString("md5")
											this["'type'"] = ExtensionType.LuaScript.ordinal
										}
									)
								}

								// Drop
								database.execSQL("DROP TABLE $tableName")

								// Rename table_new to table
								database.execSQL("ALTER TABLE `${tableName}_new` RENAME TO `${tableName}`")

								// Create indexes
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_extensions_repoID` ON `${tableName}` (`repoID`)")
							}

							// Handle novel migration
							object : RemoveMigration(2, 3) {
								override fun migrate(database: SupportSQLiteDatabase) {
									deleteColumnFromTable(database, "novels", "readerType")
								}
							}.migrate(database)

							// Create novel_settings
							run {
								database.execSQL("CREATE TABLE IF NOT EXISTS `novel_settings` (`novelID` INTEGER NOT NULL, `sortType` TEXT NOT NULL, `showOnlyReadingStatusOf` INTEGER, `showOnlyBookmarked` INTEGER NOT NULL, `showOnlyDownloaded` INTEGER NOT NULL, `reverseOrder` INTEGER NOT NULL, PRIMARY KEY(`novelID`), FOREIGN KEY(`novelID`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
								database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_novel_settings_novelID` ON `novel_settings` (`novelID`)")
							}

							// Create novel_reader_settings
							run {
								database.execSQL("CREATE TABLE IF NOT EXISTS `novel_reader_settings` (`novelID` INTEGER NOT NULL, `paragraphIndentSize` INTEGER NOT NULL, `paragraphSpacingSize` REAL NOT NULL, PRIMARY KEY(`novelID`), FOREIGN KEY(`novelID`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
								database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_novel_reader_settings_novelID` ON `novel_reader_settings` (`novelID`)")
							}
						}
					},
					object : Migration(3, 4) {
						override fun migrate(database: SupportSQLiteDatabase) {
							// Migrate extensions
							run {
								val tableName = "extensions"

								// Create new table
								database.execSQL("CREATE TABLE IF NOT EXISTS `${tableName}_new` (`id` INTEGER NOT NULL, `repoID` INTEGER NOT NULL, `name` TEXT NOT NULL, `fileName` TEXT NOT NULL, `imageURL` TEXT, `lang` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `installed` INTEGER NOT NULL, `installedVersion` TEXT, `repositoryVersion` TEXT NOT NULL, `chapterType` INTEGER NOT NULL, `md5` TEXT NOT NULL, `type` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`repoID`) REFERENCES `repositories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")

								// Migrate
								val cursor = database.query("SELECT * FROM $tableName")
								while (cursor.moveToNext()) {
									database.insert(
										"${tableName}_new",
										OnConflictStrategy.ABORT,
										ContentValues().apply {
											this["'id'"] = cursor.getInt("id")
											this["'repoID'"] = cursor.getInt("repoID")
											this["'name'"] = cursor.getString("name")
											this["'fileName'"] = cursor.getString("fileName")
											this["'imageURL'"] = cursor.getString("imageURL")
											this["'lang'"] = cursor.getString("lang")
											this["'enabled'"] = cursor.getInt("enabled")
											this["'installed'"] = cursor.getInt("installed")
											this["'installedVersion'"] =
												cursor.getStringOrNull("installedVersion")
											this["'repositoryVersion'"] =
												cursor.getString("repositoryVersion")
											this["'chapterType'"] =
												cursor.getColumnIndex("chapterType")
													.takeIf { it != -1 }?.let {
														cursor.getInt(it)
													} ?: Novel.ChapterType.STRING.key
											this["'md5'"] = cursor.getString("md5")
											this["'type'"] =
												cursor.getColumnIndex("type")
													.takeIf { it != -1 }
													?.let {
														cursor.getInt(it)
													} ?: ExtensionType.LuaScript.ordinal
										}
									)
								}

								// Drop
								database.execSQL("DROP TABLE $tableName")

								// Rename table_new to table
								database.execSQL("ALTER TABLE `${tableName}_new` RENAME TO `${tableName}`")

								// Create indexes
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_extensions_repoID` ON `${tableName}` (`repoID`)")

							}

							// Migrate extension libraries
							run {
								val tableName = "libs"

								// Create new table
								database.execSQL("CREATE TABLE IF NOT EXISTS `${tableName}_new` (`scriptName` TEXT NOT NULL, `version` TEXT NOT NULL, `repoID` INTEGER NOT NULL, PRIMARY KEY(`scriptName`), FOREIGN KEY(`repoID`) REFERENCES `repositories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")

								// Migrate
								val cursor = database.query("SELECT * FROM $tableName")
								while (cursor.moveToNext()) {
									database.insert(
										"${tableName}_new",
										OnConflictStrategy.ABORT,
										ContentValues().apply {
											this["'scriptName'"] = cursor.getString("scriptName")
											this["'version'"] = cursor.getString("version")
											this["'repoID'"] = cursor.getInt("repoID")
										}
									)
								}

								// Drop
								database.execSQL("DROP TABLE $tableName")

								// Rename table_new to table
								database.execSQL("ALTER TABLE `${tableName}_new` RENAME TO `${tableName}`")

								// Create indexes
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_libs_repoID` ON `${tableName}` (`repoID`)")
							}
						}
					},
					object : Migration(4, 5) {
						override fun migrate(database: SupportSQLiteDatabase) {
							// Download migrate
							run {
								database.execSQL("DROP INDEX IF EXISTS `index_downloads_chapterURL`")
								database.execSQL("ALTER TABLE `downloads` RENAME TO `downloads_old`")
								database.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`chapterID` INTEGER NOT NULL, `novelID` INTEGER NOT NULL, `chapterURL` TEXT NOT NULL, `chapterName` TEXT NOT NULL, `novelName` TEXT NOT NULL, `formatterID` INTEGER NOT NULL, `status` INTEGER NOT NULL, PRIMARY KEY(`chapterID`), FOREIGN KEY(`chapterID`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`novelID`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
								database.execSQL("INSERT INTO `downloads` SELECT * FROM `downloads_old`")
								database.execSQL("DROP TABLE IF EXISTS `downloads_old`")
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_chapterID` ON `downloads` (`chapterID`)")
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_novelID` ON `downloads` (`novelID`)")
							}

							// Chapter migrate
							run {
								database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_url_formatterID` ON `chapters` (`url`, `formatterID`)")
								database.execSQL("DROP INDEX IF EXISTS `index_chapters_url`")
							}

							// Novels migrate
							run {
								database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_novels_url_formatterID` ON `novels` (`url`, `formatterID`)")
							}
						}

					},
					object : Migration(5, 6) {

						override fun migrate(database: SupportSQLiteDatabase) {

							// Chapters
							// We drop the foreign key relation with extensions
							database.beginTransaction()
							try {
								database.execSQL("ALTER TABLE `chapters` RENAME TO `chapters_old`")
								database.execSQL("CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `url` TEXT NOT NULL, `novelID` INTEGER NOT NULL, `formatterID` INTEGER NOT NULL, `title` TEXT NOT NULL, `releaseDate` TEXT NOT NULL, `order` REAL NOT NULL, `readingPosition` REAL NOT NULL, `readingStatus` INTEGER NOT NULL, `bookmarked` INTEGER NOT NULL, `isSaved` INTEGER NOT NULL, FOREIGN KEY(`novelID`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
								database.execSQL("INSERT INTO `chapters` SELECT * FROM `chapters_old`")
								database.execSQL("DROP TABLE IF EXISTS `chapters_old`")
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_novelID` ON `chapters` (`novelID`)")
								database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_url_formatterID` ON `chapters` (`url`, `formatterID`)")
								database.setTransactionSuccessful()
							} finally {
								database.endTransaction()
							}

							// Novels
							database.beginTransaction()
							try {
								database.execSQL("ALTER TABLE `novels` RENAME TO `novels_old`")
								database.execSQL("CREATE TABLE IF NOT EXISTS `novels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `url` TEXT NOT NULL, `formatterID` INTEGER NOT NULL, `bookmarked` INTEGER NOT NULL, `loaded` INTEGER NOT NULL, `title` TEXT NOT NULL, `imageURL` TEXT NOT NULL, `description` TEXT NOT NULL, `language` TEXT NOT NULL, `genres` TEXT NOT NULL, `authors` TEXT NOT NULL, `artists` TEXT NOT NULL, `tags` TEXT NOT NULL, `status` INTEGER NOT NULL)")
								database.execSQL("INSERT INTO `novels` SELECT * FROM `novels_old`")
								database.execSQL("DROP TABLE IF EXISTS `novels_old`")
								database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_novels_url_formatterID` ON `novels` (`url`, `formatterID`)")
								database.setTransactionSuccessful()
							} finally {
								database.endTransaction()
							}

							// Extensions
							database.beginTransaction()
							try {
								database.execSQL("CREATE TABLE IF NOT EXISTS `installed_extension` (`id` INTEGER NOT NULL, `repoID` INTEGER NOT NULL, `name` TEXT NOT NULL, `fileName` TEXT NOT NULL, `imageURL` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` TEXT NOT NULL, `md5` TEXT NOT NULL, `type` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `chapterType` INTEGER NOT NULL, PRIMARY KEY(`id`))")
								database.execSQL("CREATE TABLE IF NOT EXISTS `repository_extension` (`repoId` INTEGER NOT NULL, `id` INTEGER NOT NULL, `name` TEXT NOT NULL, `fileName` TEXT NOT NULL, `imageURL` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` TEXT NOT NULL, `md5` TEXT NOT NULL, `type` INTEGER NOT NULL, PRIMARY KEY(`repoId`, `id`), FOREIGN KEY(`repoId`) REFERENCES `repositories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
								database.execSQL("INSERT INTO `repository_extension` SELECT `repoID`, `id`, `name`, `fileName`, `imageURL`, `lang`, `repositoryVersion`, `md5`, `type` FROM `extensions`")
								database.execSQL("INSERT INTO `installed_extension` SELECT `id`, `repoID`, `name`, `fileName`, `imageURL`, `lang`, `installedVersion`, `md5`, `type`, `enabled`, `chapterType` FROM `extensions` WHERE `installed`=1")
								database.execSQL("DROP TABLE IF EXISTS `extensions`")
								database.execSQL("CREATE INDEX IF NOT EXISTS `index_repository_extension_repoId` ON `repository_extension` (`repoId`)")
								database.setTransactionSuccessful()
							} finally {
								database.endTransaction()
							}

							// Extension Libs
							database.beginTransaction()
							try {
								database.execSQL("ALTER TABLE `libs` RENAME TO `libs_old`")
								database.execSQL("CREATE TABLE IF NOT EXISTS `libs` (`scriptName` TEXT NOT NULL, `version` TEXT NOT NULL, `repoID` INTEGER NOT NULL, PRIMARY KEY(`scriptName`))")
								database.execSQL("INSERT INTO `libs` SELECT * FROM `libs_old`")
								database.execSQL("DROP TABLE IF EXISTS `libs_old`")
								database.setTransactionSuccessful()
							} finally {
								database.endTransaction()
							}

						}

					}
				).build()

			GlobalScope.launch {
				try {
					databaseShosetsu.repositoryDao.initializeData()
				} catch (e: SQLiteException) {
					e.printStackTrace()
				}
			}
			return databaseShosetsu
		}
	}
}